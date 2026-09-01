#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT/scripts/verify-structure.sh"
"$ROOT/scripts/verify-domain.sh"
"$ROOT/scripts/verify-native.sh"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -n "$SDK_ROOT" && -d "$SDK_ROOT/platforms/android-36" ]]; then
    "$ROOT/gradlew" --stacktrace --no-daemon \
        -Pkotlin.compiler.execution.strategy=in-process \
        :domain:test \
        :app:testDebugUnitTest \
        :app:lintDebug \
        :app:assembleDebug
    python3 "$ROOT/tools/verify_apk_native_runtime.py" \
        "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
else
    echo "[SKIP] Vérification Android: SDK android-36 non détecté." >&2
    echo "       Les vérifications Kotlin pur et DSP hôte ont été exécutées." >&2
fi
