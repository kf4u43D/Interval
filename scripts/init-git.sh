#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_URL="${1:-}"
if [[ -z "$REMOTE_URL" ]]; then
    echo "Usage: $0 <git-remote-url>" >&2
    exit 2
fi
cd "$ROOT"
if [[ ! -d .git ]]; then
    git init -b main
fi
if EXISTING="$(git remote get-url origin 2>/dev/null)"; then
    if [[ "$EXISTING" != "$REMOTE_URL" ]]; then
        echo "Le remote origin existe déjà: $EXISTING" >&2
        echo "Aucune modification. Utiliser explicitement 'git remote set-url origin …' si ce remplacement est intentionnel." >&2
        exit 3
    fi
    echo "Remote origin déjà configuré avec cette URL."
else
    git remote add origin "$REMOTE_URL"
    printf 'Remote origin ajouté: %s\n' "$(git remote get-url origin)"
fi
printf 'Aucun commit ni push n’a été effectué.\n'
