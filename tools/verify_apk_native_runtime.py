#!/usr/bin/env python3
"""Fail when the APK omits a native runtime required by interval_audio."""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path


INTERVAL_AUDIO = re.compile(r"^lib/([^/]+)/libinterval_audio[.]so$")
REQUIRED_RUNTIME_LIBRARIES = ("libc++_shared.so", "liboboe.so")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_apk_native_runtime.py <apk>", file=sys.stderr)
        return 2

    apk = Path(sys.argv[1])
    if not apk.is_file():
        print(f"APK absent: {apk}", file=sys.stderr)
        return 2

    with zipfile.ZipFile(apk) as archive:
        entries = set(archive.namelist())

    abis = sorted(
        match.group(1)
        for entry in entries
        if (match := INTERVAL_AUDIO.match(entry)) is not None
    )
    if "arm64-v8a" not in abis:
        print("libinterval_audio.so arm64-v8a absent de l'APK", file=sys.stderr)
        return 1

    missing = [
        f"lib/{abi}/{library}"
        for abi in abis
        for library in REQUIRED_RUNTIME_LIBRARIES
        if f"lib/{abi}/{library}" not in entries
    ]
    if missing:
        print("Runtime natif incomplet dans l'APK:", file=sys.stderr)
        for entry in missing:
            print(f"  - {entry}", file=sys.stderr)
        return 1

    print(f"Runtime APK: OK - {', '.join(abis)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
