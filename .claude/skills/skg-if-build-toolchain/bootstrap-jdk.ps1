<#
.SYNOPSIS
    Downloads the portable Temurin JDK into .tools/ if it isn't there yet. Idempotent.

.DESCRIPTION
    This is the ONLY place in the repo that knows how to provision a JDK. Both activate.ps1
    and activate.sh call it rather than carrying their own copy of the recipe.

    The Java feature version is NOT hardcoded here - it is read from pom.xml's
    <maven.compiler.release>, which is the single source of truth for this repo's Java
    version. Bump it there and this script follows.

    Maven is deliberately absent: builds go through the ./mvnw wrapper, which pins its own
    version in .mvn/wrapper/maven-wrapper.properties.

    Unlike activate.ps1, this script does NOT need to be dot-sourced - it mutates the
    filesystem, not the environment.
#>
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$pom = Join-Path $repoRoot 'pom.xml'

$match = [regex]::Match((Get-Content -Raw $pom), '<maven\.compiler\.release>\s*(\d+)\s*<')
if (-not $match.Success) {
    throw "Could not read <maven.compiler.release> from $pom - it is the single source of truth for this repo's Java version. Check that the property still exists and holds a bare major version (e.g. 21)."
}
$jdkMajor = $match.Groups[1].Value

$tools = Join-Path $repoRoot '.tools'
$jdkHome = Join-Path $tools "jdk-$jdkMajor"

if (Test-Path (Join-Path $jdkHome 'bin\java.exe')) {
    Write-Verbose "JDK $jdkMajor already present at $jdkHome"
    return
}

Write-Host "Provisioning Temurin JDK $jdkMajor into $jdkHome ..."
New-Item -ItemType Directory -Force $tools | Out-Null

$zip = Join-Path $tools "temurin$jdkMajor.zip"
$extract = Join-Path $tools "jdk-$jdkMajor-extracted"

# Adoptium's "latest GA" redirect - no build number in the URL, so it can't go stale.
$url = "https://api.adoptium.net/v3/binary/latest/$jdkMajor/ga/windows/x64/jdk/hotspot/normal/eclipse"
Invoke-WebRequest -Uri $url -OutFile $zip
Expand-Archive -Path $zip -DestinationPath $extract -Force

# Adoptium zips contain a single top-level jdk-<major>.x.x+y folder; rename it to the
# stable, version-suffix-free path everything else refers to.
$extracted = Get-ChildItem -Path $extract -Directory -Filter "jdk-$jdkMajor*" | Select-Object -First 1
if (-not $extracted) {
    throw "Downloaded archive did not contain an expected jdk-$jdkMajor* directory under $extract"
}
Move-Item $extracted.FullName $jdkHome

Remove-Item $zip, $extract -Recurse -Force
Write-Host "JDK $jdkMajor ready at $jdkHome"
