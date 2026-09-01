#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
errors=0
warnings=0
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

ok() { printf '  [OK] %s\n' "$*"; }
warn() { printf '  [WARN] %s\n' "$*"; warnings=$((warnings + 1)); }
fail() { printf '  [FAIL] %s\n' "$*"; errors=$((errors + 1)); }

printf 'Interval Tablet — diagnostic\n'
printf 'Racine: %s\n\n' "$ROOT"

if command -v java >/dev/null 2>&1; then
    java_version="$(java -version 2>&1 | head -n 1)"
    ok "Java: $java_version"
else
    fail "Java absent (JDK 17 ou 21 requis)."
fi

if command -v python3 >/dev/null 2>&1; then
    ok "Python: $(python3 --version 2>&1)"
else
    fail "Python 3.11+ absent; vérification de structure indisponible."
fi

if command -v kotlinc >/dev/null 2>&1; then
    ok "Kotlin hôte: $(kotlinc -version 2>&1 | head -n 1)"
else
    warn "kotlinc absent; verify-domain utilisera Gradle si possible."
fi

for tool in cmake c++ git; do
    if command -v "$tool" >/dev/null 2>&1; then
        ok "$tool: $(command -v "$tool")"
    else
        fail "$tool absent."
    fi
done

if command -v ninja >/dev/null 2>&1; then
    ok "ninja: $(command -v ninja)"
elif [[ -n "$SDK_ROOT" && -x "$SDK_ROOT/cmake/3.22.1/bin/ninja" ]]; then
    ok "ninja: CMake SDK 3.22.1"
elif [[ -n "$SDK_ROOT" && -x "$SDK_ROOT/cmake/3.22.1/bin/ninja.exe" ]]; then
    ok "ninja: CMake SDK 3.22.1"
else
    fail "ninja absent du PATH et de CMake SDK 3.22.1."
fi

if command -v adb >/dev/null 2>&1; then
    ok "adb: $(adb version 2>/dev/null | head -n 1)"
else
    warn "adb absent du PATH."
fi

if [[ -n "$SDK_ROOT" && -d "$SDK_ROOT" ]]; then
    ok "Android SDK: $SDK_ROOT"
    [[ -d "$SDK_ROOT/platforms/android-36" ]] && ok "Platform android-36" || fail "Platform android-36 absente."
    [[ -d "$SDK_ROOT/ndk/28.2.13676358" ]] && ok "NDK 28.2.13676358" || fail "NDK 28.2.13676358 absent."
    [[ -d "$SDK_ROOT/cmake/3.22.1" ]] && ok "CMake SDK 3.22.1" || warn "CMake SDK 3.22.1 absent; AGP pourra tenter de l’installer."
else
    warn "ANDROID_SDK_ROOT/ANDROID_HOME non défini; build Android indisponible."
fi

if command -v codex >/dev/null 2>&1; then
    ok "Codex CLI: $(codex --version 2>/dev/null | head -n 1)"
else
    warn "Codex CLI absent; l’extension VS Code peut néanmoins être utilisée."
fi

printf '\nRésultat: %d erreur(s), %d avertissement(s).\n' "$errors" "$warnings"
if (( errors > 0 )); then
    exit 1
fi
