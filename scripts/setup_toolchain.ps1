# One-time bootstrap of Android command-line toolchain (no admin required)
# Output: C:\Users\song9\.local\share\android-toolchain
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$Root = 'C:\Users\song9\.local\share\android-toolchain'
$Dl = Join-Path $Root 'downloads'
$JdkDir = Join-Path $Root 'jdk17'
$SdkDir = Join-Path $Root 'sdk'
New-Item -ItemType Directory -Force -Path $Dl, $SdkDir | Out-Null

function Get-File($Url, $OutFile) {
    if (Test-Path $OutFile) { Write-Host "exists, skip download: $OutFile"; return }
    Write-Host "download: $Url"
    Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing -TimeoutSec 1800
}

$jdkZip = Join-Path $Dl 'jdk17.zip'
Get-File 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' $jdkZip
if (-not (Test-Path $JdkDir)) {
    $tmp = Join-Path $Root 'tmp-jdk'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    [System.IO.Compression.ZipFile]::ExtractToDirectory($jdkZip, $tmp)
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    Move-Item $inner.FullName $JdkDir
    Remove-Item $tmp -Recurse -Force
}

$cmdZip = Join-Path $Dl 'cmdline-tools.zip'
Get-File 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' $cmdZip
$cmdLatest = Join-Path $SdkDir 'cmdline-tools\latest'
if (-not (Test-Path $cmdLatest)) {
    $tmp2 = Join-Path $Root 'tmp-cmd'
    if (Test-Path $tmp2) { Remove-Item $tmp2 -Recurse -Force }
    [System.IO.Compression.ZipFile]::ExtractToDirectory($cmdZip, $tmp2)
    New-Item -ItemType Directory -Force -Path (Join-Path $SdkDir 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp2 'cmdline-tools') $cmdLatest
    Remove-Item $tmp2 -Recurse -Force
}

$env:JAVA_HOME = $JdkDir
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir
$env:PATH = "$JdkDir\bin;$SdkDir\cmdline-tools\latest\bin;$SdkDir\platform-tools;$env:PATH"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
& "$JdkDir\bin\java.exe" -version

$sdkmanager = Join-Path $SdkDir 'cmdline-tools\latest\bin\sdkmanager.bat'
$licDir = Join-Path $SdkDir 'licenses'
New-Item -ItemType Directory -Force -Path $licDir | Out-Null
Set-Content -Path (Join-Path $licDir 'android-sdk-license') -NoNewline -Value @"
24333f8a63b6825ea9c5514f83c2829b004d1fee
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
"@
Set-Content -Path (Join-Path $licDir 'android-sdk-preview-license') -NoNewline -Value '84831b9409646a918e30573bab4c9c91346d8abd'
Set-Content -Path (Join-Path $licDir 'android-googletv-license') -NoNewline -Value '601085b94cd7ee50d69cba87b45468379d5b874e'
Set-Content -Path (Join-Path $licDir 'android-sdk-arm-dbt-license') -NoNewline -Value '859f317696f67ef3d7f30a857f25ace6e82fff3d'

Write-Host 'installing platforms / build-tools / platform-tools ...'
& $sdkmanager --install 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'

Write-Host '--- verify ---'
& "$SdkDir\platform-tools\adb.exe" version
Get-ChildItem (Join-Path $SdkDir 'build-tools') | Select-Object Name
Get-ChildItem (Join-Path $SdkDir 'platforms') | Select-Object Name
Write-Host 'toolchain ready'
