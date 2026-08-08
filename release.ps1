# Flash Capsule 一键发布：编译 → 成功则 commit + push 到 GitHub
# 用法：  pwsh release.ps1 -Message "v0.x.y: 说明动了啥"
param(
    [Parameter(Mandatory = $true)][string]$Message
)
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "D:\Android\Sdk"
$proj = $PSScriptRoot
$gradle = "D:\Tools\gradle-8.7\bin\gradle.bat"

Write-Output "== 编译 =="
& $gradle -p $proj ":app:assembleDebug" --no-daemon
if ($LASTEXITCODE -ne 0) { Write-Error "编译失败，未同步。"; exit 1 }

Write-Output "== 同步 GitHub =="
git -C $proj add -A
# 无改动时 commit 会失败，忽略即可
git -C $proj commit -m $Message 2>&1 | Out-Null
git -C $proj push
Write-Output "== 完成：已推送到 GitHub =="

$apk = Join-Path $proj "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) { Write-Output ("APK: {0}" -f $apk) }
