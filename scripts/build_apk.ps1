# Build S520J APK and copy it to dist/ with the stage tag in the file name.
param(
    [string]$Tag = "dev",
    [switch]$Release
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Toolchain = 'C:\Users\song9\.local\share\android-toolchain'

$env:JAVA_HOME = Join-Path $Toolchain 'jdk17'
$env:ANDROID_HOME = Join-Path $Toolchain 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

$DistDir = Join-Path $ProjectRoot 'dist'
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

$versionCode = 1
if ($Tag -match 'v(\d+)\.(\d+)\.(\d+)') {
    $versionCode = ([int]$Matches[1] * 10000) + ([int]$Matches[2] * 100) + [int]$Matches[3]
}

$variant = if ($Release) { 'release' } else { 'debug' }
$gradleTask = if ($Release) { 'assembleRelease' } else { 'assembleDebug' }

Write-Host "==> gradlew $gradleTask (tag=$Tag, versionCode=$versionCode)"
Push-Location $ProjectRoot
try {
    & "$ProjectRoot\gradlew.bat" $gradleTask `
        "-Ps520jVersionName=$Tag" `
        "-Ps520jVersionCode=$versionCode" `
        --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

$apkSource = Join-Path $ProjectRoot "app\build\outputs\apk\$variant\app-$variant.apk"
if (-not (Test-Path $apkSource)) { throw "APK not found: $apkSource" }

$apkName = "S520J-$Tag-$variant.apk"
$apkTarget = Join-Path $DistDir $apkName
Copy-Item $apkSource $apkTarget -Force

$info = @(
    "tag=$Tag",
    "variant=$variant",
    "versionCode=$versionCode",
    "builtAt=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "apk=$apkName",
    "sizeBytes=$((Get-Item $apkTarget).Length)",
    "jdk=$env:JAVA_HOME",
    "androidSdk=$env:ANDROID_HOME"
)
Set-Content -Path (Join-Path $DistDir 'build-info.txt') -Value $info -Encoding UTF8
Write-Host "OK -> $apkTarget"
