#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGE="${1:-}"
case "$STAGE" in
    1) PROMPT="$ROOT/codex/prompts/01_midi_core.md" ;;
    2) PROMPT="$ROOT/codex/prompts/02_tone_row_transport.md" ;;
    3) PROMPT="$ROOT/codex/prompts/03_audio_ui_hardening.md" ;;
    4) PROMPT="$ROOT/codex/prompts/04_v2_performance_midi_learn.md" ;;
    5) PROMPT="$ROOT/codex/prompts/05_v2_1_performance_surface.md" ;;
    6) PROMPT="$ROOT/codex/prompts/06_v2_2_two_hand_low_latency.md" ;;
    *) echo "Usage: $0 <1|2|3|4|5|6>" >&2; exit 2 ;;
esac
if ! command -v codex >/dev/null 2>&1; then
    echo "Codex CLI absent. Installer/authentifier Codex ou utiliser l’extension VS Code." >&2
    exit 3
fi
cd "$ROOT"
if [[ ! -d .git ]]; then
    git init -b main >/dev/null
    echo "Dépôt Git local initialisé sans remote, requis par codex exec." >&2
fi
mkdir -p .codex/runs
RUN_LOG=".codex/runs/stage-${STAGE}-$(date +%Y%m%d-%H%M%S).last-message.md"
{
    cat CODEX_SUPERPROMPT.md
    printf '\n\n# Prompt d’étape sélectionné\n\n'
    cat "$PROMPT"
} | codex exec --sandbox workspace-write - --output-last-message "$RUN_LOG"
