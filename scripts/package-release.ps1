[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildScriptPath = Join-Path $projectRoot "app/build.gradle.kts"
$buildScript = Get-Content -LiteralPath $buildScriptPath -Raw
$versionMatch = [regex]::Match($buildScript, 'val\s+apkVersionName\s*=\s*"([^"]+)"')
if (-not $versionMatch.Success) {
    throw "Could not read apkVersionName from app/build.gradle.kts."
}

$versionName = $versionMatch.Groups[1].Value
& (Join-Path $projectRoot "gradlew.bat") :app:packageReleaseApk --no-parallel
if ($LASTEXITCODE -ne 0) {
    throw "Release packaging failed."
}

$artifactFile = Get-ChildItem -LiteralPath (Join-Path $projectRoot "app/build/distributions/release") -Filter "*-$versionName.apk" | Select-Object -First 1
if (-not $artifactFile) {
    throw "Release APK was not created for version: $versionName"
}
$artifact = $artifactFile.FullName

$artifactInfo = Get-Item -LiteralPath $artifact
$maximumBytes = 10MB
if ($artifactInfo.Length -gt $maximumBytes) {
    throw "Release APK exceeds the 10 MiB size limit: $($artifactInfo.Length) bytes."
}

$artifactInfo | Select-Object Name, Length, LastWriteTime, FullName
