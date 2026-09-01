#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$ROOT/gradlew" --refresh-dependencies \
    :domain:dependencies \
    :app:dependencies \
    :domain:test \
    :app:assembleDebug
