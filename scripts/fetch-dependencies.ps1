$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
& (Join-Path $Root "gradlew.bat") --refresh-dependencies :domain:dependencies :app:dependencies :domain:test :app:assembleDebug
exit $LASTEXITCODE
