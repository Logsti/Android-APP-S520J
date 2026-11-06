# Build S520J APK and copy it to dist/ with the stage tag in the file name.
param(
    [string]$Tag = "dev",
    [switch]$Release,
    [string]$Jdk = "",
    [string]$Sdk = ""
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

# 优先使用本项目便携工具链；不存在时回落到本机已安装的 JDK 17 / Android SDK。
if ($Jdk) {
    $jdkPath = $Jdk
} elseif (Test-Path 'C:\Users\song9\.local\share\android-toolchain\jdk17') {
    $jdkPath = 'C:\Users\song9\.local\share\android-toolchain\jdk17'
} elseif ($env:JAVA_HOME) {
    $jdkPath = $env:JAVA_HOME
} else {
    $jdkPath = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot'
}

if ($Sdk) {
    $sdkPath = $Sdk
} elseif (Test-Path 'C:\Users\song9\.local\share\android-toolchain\sdk') {
    $sdkPath = 'C:\Users\song9\.local\share\android-toolchain\sdk'
} elseif (Test-Path 'C:\Android\Sdk') {
    $sdkPath = 'C:\Android\Sdk'
} else {
    throw "未找到 Android SDK，请用 -Sdk 指定路径"
}

if (-not (Test-Path (Join-Path $jdkPath 'bin\java.exe'))) { throw "JDK 不存在：$jdkPath" }
if (-not (Test-Path (Join-Path $sdkPath 'platforms'))) { throw "Android SDK 不完整：$sdkPath" }

$env:JAVA_HOME = $jdkPath
$env:ANDROID_HOME = $sdkPath
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

$DistDir = Join-Path $ProjectRoot 'dist'
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

$versionCode = 1
if ($Tag -match 'v(\d+)\.(\d+)\.(\d+)') {
    # 与历史产物一致：v0.1.4 -> 104，v0.2.3 -> 203，v0.3.0 -> 300，v1.0.0 -> 1000
    $versionCode = ([int]$Matches[1] * 1000) + ([int]$Matches[2] * 100) + [int]$Matches[3]
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
