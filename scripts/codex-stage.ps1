param([Parameter(Mandatory = $true)][ValidateSet(1, 2, 3, 4, 5, 6, 7, 8)][int] $Stage)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not (Get-Command codex -ErrorAction SilentlyContinue)) { throw "Codex CLI absent." }
$Prompt = switch ($Stage) {
    1 { Join-Path $Root "codex\prompts\01_midi_core.md" }
    2 { Join-Path $Root "codex\prompts\02_tone_row_transport.md" }
    3 { Join-Path $Root "codex\prompts\03_audio_ui_hardening.md" }
    4 { Join-Path $Root "codex\prompts\04_v2_performance_midi_learn.md" }
    5 { Join-Path $Root "codex\prompts\05_v2_1_performance_surface.md" }
    6 { Join-Path $Root "codex\prompts\06_v2_2_two_hand_low_latency.md" }
    7 { Join-Path $Root "codex\prompts\07_v2_3_direct_harmony_arpeggio.md" }
    8 { Join-Path $Root "codex\prompts\08_v2_4_workstation_surface.md" }
}
Set-Location $Root
if (-not (Test-Path ".git")) { git init -b main | Out-Null }
New-Item -ItemType Directory -Force ".codex\runs" | Out-Null
$RunLog = ".codex\runs\stage-$Stage-$(Get-Date -Format yyyyMMdd-HHmmss).last-message.md"
$Combined = (Get-Content "CODEX_SUPERPROMPT.md" -Raw) + "`n`n# Prompt d'etape selectionne`n`n" + (Get-Content $Prompt -Raw)
$Combined | codex exec --sandbox workspace-write - --output-last-message $RunLog
exit $LASTEXITCODE
