#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DISTRIBUTION_SHA="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
WRAPPER_SHA="81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"

"$ROOT/gradlew" wrapper \
    --gradle-version 8.13 \
    --distribution-type bin \
    --gradle-distribution-sha256-sum "$DISTRIBUTION_SHA"

JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
[[ -f "$JAR" ]] || { echo "gradle-wrapper.jar n’a pas été généré." >&2; exit 2; }
if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL="$(sha256sum "$JAR" | awk '{print $1}')"
else
    ACTUAL="$(python3 - "$JAR" <<'PY'
import hashlib, pathlib, sys
print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())
PY
)"
fi
[[ "$ACTUAL" == "$WRAPPER_SHA" ]] || {
    echo "Empreinte inattendue du Gradle Wrapper: $ACTUAL" >&2
    exit 3
}
echo "Gradle Wrapper officiel 8.13 généré et vérifié."
