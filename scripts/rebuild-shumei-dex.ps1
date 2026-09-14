param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDex,
    [Parameter(Mandatory = $true)]
    [string]$AndroidCommandLineLib,
    [string]$OutputDex = (Join-Path $PSScriptRoot '..\app\src\main\assets\shumei.dex'),
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$sourcePath = (Resolve-Path -LiteralPath $SourceDex).Path
$libraryPath = (Resolve-Path -LiteralPath $AndroidCommandLineLib).Path
$outputPath = [IO.Path]::GetFullPath($OutputDex)
$requiredJars = @('smali-dexlib2-3.0.3.jar', 'guava-31.1-jre.jar', 'failureaccess-1.0.1.jar', 'jsr305-3.0.2.jar')
$jarPaths = foreach ($name in $requiredJars) {
    $matches = @(Get-ChildItem -LiteralPath $libraryPath -Filter $name -File -Recurse)
    if ($matches.Count -ne 1) { throw "Expected exactly one local dependency: $name" }
    $matches[0].FullName
}
$classPath = $jarPaths -join [IO.Path]::PathSeparator
$temporaryRoot = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) 'leapauto-shumei-build'))
$temporaryDirectory = Join-Path $temporaryRoot ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null

try {
    & javac -encoding UTF-8 -cp $classPath -d $temporaryDirectory (Join-Path $PSScriptRoot 'shumei\BuildShumeiDex.java')
    if ($LASTEXITCODE -ne 0) { throw 'Shumei DEX builder compilation failed.' }
    $runClassPath = $temporaryDirectory + [IO.Path]::PathSeparator + $classPath
    $mode = if ($CheckOnly) { 'check' } else { 'build' }
    & java -cp $runClassPath BuildShumeiDex $mode $sourcePath $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'Shumei DEX dependency or preservation validation failed.' }
} finally {
    $resolvedTemporary = [IO.Path]::GetFullPath($temporaryDirectory)
    if (-not $resolvedTemporary.StartsWith($temporaryRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove an unexpected build directory.'
    }
    Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force -ErrorAction SilentlyContinue
}
