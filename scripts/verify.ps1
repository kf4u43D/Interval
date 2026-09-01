$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
& (Join-Path $PSScriptRoot "verify-structure.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot "verify-domain.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot "verify-native.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$Sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if ($Sdk -and (Test-Path (Join-Path $Sdk "platforms\android-36"))) {
    & (Join-Path $Root "gradlew.bat") `
        :domain:test `
        :app:testDebugUnitTest `
        :app:lintDebug `
        :app:assembleDebug `
        --no-daemon `
        "-Pkotlin.compiler.execution.strategy=in-process"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $Apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
    $RuntimeCheck = Join-Path $Root "tools\verify_apk_native_runtime.py"
    if (Get-Command py -ErrorAction SilentlyContinue) {
        & py -3 $RuntimeCheck $Apk
    } else {
        & python $RuntimeCheck $Apk
    }
    exit $LASTEXITCODE
}
Write-Host "[SKIP] Verification Android: SDK android-36 non detecte."
