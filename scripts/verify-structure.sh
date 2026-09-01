#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if command -v python3 >/dev/null 2>&1; then
    exec python3 "$ROOT/tools/verify_structure.py"
elif command -v python >/dev/null 2>&1; then
    exec python "$ROOT/tools/verify_structure.py"
else
    echo "Python 3.11+ est requis pour vérifier la structure." >&2
    exit 2
fi
