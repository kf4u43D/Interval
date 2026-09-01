#!/usr/bin/env python3
"""Validate the portable workspace without resolving external dependencies."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SKIP_PARTS = {
    ".git",
    ".gradle",
    ".android",
    ".idea",
    ".kotlin",
    ".tmp",
    ".cxx",
    ".externalNativeBuild",
    "build",
    "node_modules",
    "__pycache__",
}
REQUIRED_FILES = {
    "README.md",
    "AGENTS.md",
    "CODEX_SUPERPROMPT.md",
    "CODEX_MILESTONES.md",
    "THIRD_PARTY_NOTICES.md",
    "docs/PRODUCT_BRIEF.md",
    "docs/BEHAVIOR_SPEC.md",
    "docs/MIDI_SPEC.md",
    "docs/AUDIO_DSP_SPEC.md",
    "docs/ARCHITECTURE.md",
    "docs/TEST_STRATEGY.md",
    "docs/ACCEPTANCE_CRITERIA.md",
    "docs/MANUAL_TRACEABILITY.md",
    "docs/HARDWARE_TEST_PROTOCOL.md",
    "docs/IMPLEMENTATION_STATUS.md",
    "docs/implementation/STAGE_PLAN_TEMPLATE.md",
    "codex/prompts/01_midi_core.md",
    "codex/prompts/02_tone_row_transport.md",
    "codex/prompts/03_audio_ui_hardening.md",
    "gradle/libs.versions.toml",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/cpp/CMakeLists.txt",
    "domain/build.gradle.kts",
    ".github/workflows/ci.yml",
    ".github/dependabot.yml",
}
SECRET_PATTERNS = {
    "AWS access key": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    "GitHub token": re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"),
    "private key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
}
ABSOLUTE_USER_PATHS = {
    "Linux user path": re.compile(r"(?<![A-Za-z0-9])/(?:home|mnt/data)/[^\s/'\"]+"),
    "macOS user path": re.compile(r"(?<![A-Za-z0-9])/Users/[^\s/'\"]+"),
    "Windows user path": re.compile(r"\b[A-Za-z]:\\Users\\[^\\\s]+"),
    "sandbox artifact URI": re.compile(r"sandbox:/mnt/data/"),
}
TEXT_SUFFIXES = {
    ".c",
    ".cc",
    ".cpp",
    ".h",
    ".hpp",
    ".java",
    ".json",
    ".kt",
    ".kts",
    ".md",
    ".properties",
    ".ps1",
    ".py",
    ".sh",
    ".toml",
    ".txt",
    ".xml",
    ".yaml",
    ".yml",
}
TEXT_NAMES = {"gradlew", "LICENSE"}


def included_files() -> list[Path]:
    files: list[Path] = []
    for path in ROOT.rglob("*"):
        rel = path.relative_to(ROOT)
        if any(part in SKIP_PARTS for part in rel.parts):
            continue
        if path.is_file():
            files.append(path)
    return sorted(files)


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []
    files = included_files()

    missing = sorted(REQUIRED_FILES - {relative(path) for path in files})
    errors.extend(f"fichier requis absent: {item}" for item in missing)

    for path in files:
        rel = relative(path)
        if path.stat().st_size == 0:
            errors.append(f"fichier vide: {rel}")
            continue

        is_text = path.suffix.lower() in TEXT_SUFFIXES or path.name in TEXT_NAMES
        if not is_text:
            continue

        try:
            raw = path.read_bytes()
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            errors.append(f"UTF-8 invalide: {rel}: {exc}")
            continue

        if b"\r\n" in raw or b"\r" in raw.replace(b"\r\n", b""):
            warnings.append(f"fin de ligne non LF: {rel}")
        if "\x00" in text:
            errors.append(f"octet NUL dans un fichier texte: {rel}")
        if any(line.endswith((" ", "\t")) for line in text.splitlines()):
            warnings.append(f"espaces de fin de ligne: {rel}")

        for label, pattern in SECRET_PATTERNS.items():
            if pattern.search(text):
                errors.append(f"secret potentiel ({label}): {rel}")
        if rel != "tools/verify_structure.py":
            for label, pattern in ABSOLUTE_USER_PATHS.items():
                if pattern.search(text):
                    errors.append(f"chemin utilisateur absolu ({label}): {rel}")

        try:
            if path.suffix.lower() == ".json" or path.name.endswith(".code-workspace"):
                json.loads(text)
            elif path.suffix.lower() == ".toml":
                tomllib.loads(text)
            elif path.suffix.lower() == ".xml":
                ET.fromstring(text)
        except Exception as exc:  # precise type varies by parser
            errors.append(f"syntaxe invalide: {rel}: {exc}")

    package_audit = os.environ.get("PACKAGE_AUDIT") == "1"
    if package_audit:
        forbidden_dirs = [
            ROOT / "build",
            ROOT / "native-tests/build",
            ROOT / ".gradle",
            ROOT / ".idea",
            ROOT / ".cxx",
        ]
        for path in forbidden_dirs:
            if path.exists():
                errors.append(f"artefact de build présent pendant l’audit ZIP: {relative(path)}")
        for path in ROOT.rglob("*"):
            if path.is_file() and path.suffix.lower() in {".apk", ".aab", ".jks", ".keystore"}:
                errors.append(f"artefact sensible ou généré dans le ZIP: {relative(path)}")

    state_path = ROOT / ".codex/state.json"
    if state_path.exists():
        state = json.loads(state_path.read_text(encoding="utf-8"))
        if state.get("currentStage") not in {1, 2, 3}:
            errors.append(".codex/state.json: currentStage doit valoir 1, 2 ou 3")
        if state.get("status") not in {"not-started", "in-progress", "blocked", "complete"}:
            errors.append(".codex/state.json: status non reconnu")

    if errors:
        print("Structure: ÉCHEC", file=sys.stderr)
        for item in errors:
            print(f"  [FAIL] {item}", file=sys.stderr)
        for item in warnings:
            print(f"  [WARN] {item}", file=sys.stderr)
        return 1

    digest = hashlib.sha256()
    for path in files:
        digest.update(relative(path).encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")

    print(f"Structure: OK — {len(files)} fichiers, empreinte logique {digest.hexdigest()[:16]}")
    for item in warnings:
        print(f"  [WARN] {item}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
