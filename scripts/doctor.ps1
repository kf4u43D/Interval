$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Errors = 0
$Warnings = 0
$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
function Ok($Message) { Write-Host "  [OK] $Message" }
function Warn($Message) { Write-Host "  [WARN] $Message"; $script:Warnings++ }
function Fail($Message) { Write-Host "  [FAIL] $Message"; $script:Errors++ }

Write-Host "Interval Tablet - diagnostic"
Write-Host "Racine: $Root`n"

if ((Get-Command py -ErrorAction SilentlyContinue) -or (Get-Command python -ErrorAction SilentlyContinue)) {
    Ok "Python 3 disponible"
} else {
    Fail "Python 3.11+ absent; verification de structure indisponible"
}
foreach ($Tool in @("java", "cmake", "git")) {
    if (Get-Command $Tool -ErrorAction SilentlyContinue) { Ok "$Tool disponible" } else { Fail "$Tool absent" }
}
$NinjaCommand = Get-Command ninja -ErrorAction SilentlyContinue
$SdkNinja = if ($Sdk) { Join-Path $Sdk "cmake\3.22.1\bin\ninja.exe" } else { $null }
if ($NinjaCommand) {
    Ok "ninja disponible"
} elseif ($SdkNinja -and (Test-Path $SdkNinja)) {
    Ok "ninja disponible via CMake SDK 3.22.1"
} else {
    Fail "ninja absent du PATH et de CMake SDK 3.22.1"
}
if (Get-Command kotlinc -ErrorAction SilentlyContinue) { Ok "kotlinc disponible" } else { Warn "kotlinc absent" }
if (Get-Command adb -ErrorAction SilentlyContinue) { Ok "adb disponible" } else { Warn "adb absent du PATH" }
if (Get-Command codex -ErrorAction SilentlyContinue) { Ok "Codex CLI disponible" } else { Warn "Codex CLI absent" }

if ($Sdk -and (Test-Path $Sdk)) {
    Ok "Android SDK: $Sdk"
    if (Test-Path (Join-Path $Sdk "platforms\android-36")) { Ok "Platform android-36" } else { Fail "Platform android-36 absente" }
    if (Test-Path (Join-Path $Sdk "ndk\28.2.13676358")) { Ok "NDK 28.2.13676358" } else { Fail "NDK requis absent" }
} else {
    Warn "ANDROID_SDK_ROOT/ANDROID_HOME non defini"
}

Write-Host "`nResultat: $Errors erreur(s), $Warnings avertissement(s)."
if ($Errors -gt 0) { exit 1 }
