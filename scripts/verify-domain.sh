#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT/build/host-domain"
mkdir -p "$BUILD_DIR"

if ! command -v kotlinc >/dev/null 2>&1; then
    echo "kotlinc absent; verification via le build Gradle autonome du domaine." >&2
    exec "$ROOT/gradlew" \
        --project-dir "$ROOT/domain" \
        -Pkotlin.compiler.execution.strategy=in-process \
        test
fi

mapfile -t SOURCES < <(find "$ROOT/domain/src/main/kotlin" -name '*.kt' -type f | sort)
SOURCES+=("$ROOT/app/src/main/kotlin/dev/intervaltablet/midi/MidiMessageCodec.kt")
SOURCES+=("$ROOT/tools/DomainSmoke.kt")

kotlinc "${SOURCES[@]}" \
    -Werror \
    -include-runtime \
    -d "$BUILD_DIR/domain-smoke.jar"
java -jar "$BUILD_DIR/domain-smoke.jar"
