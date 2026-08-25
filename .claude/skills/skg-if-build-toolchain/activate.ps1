<#
.SYNOPSIS
    Puts the portable JDK on PATH for the current PowerShell session. Idempotent.

.DESCRIPTION
    DOT-SOURCE THIS, do not execute it:

        . .\.claude\skills\skg-if-build-toolchain\activate.ps1

    A script run as a child process cannot mutate its caller's environment, so
    `.\activate.ps1` (no leading dot-space) appears to succeed while leaving JAVA_HOME and
    PATH untouched - and the very next `.\mvnw.cmd` then fails for a reason that looks
    nothing like the actual cause.

    Provisions the JDK first if it's missing (see bootstrap-jdk.ps1), so this is safe to
    call unconditionally before every command - there is no separate "one-time setup" step
    to remember.

    Sets nothing at user or machine scope; the changes die with the process.

    Maven is intentionally not handled here. Build through the ./mvnw wrapper, which pins
    its own version in .mvn/wrapper/maven-wrapper.properties.

    Restores the caller's $ErrorActionPreference before returning. Dot-sourcing runs in the
    caller's scope, so leaving this at 'Stop' would otherwise survive into whatever command
    follows on the same line (the only supported way to invoke this, since env vars don't
    persist across separate tool calls) - and .\mvnw.cmd always writes at least one benign
    stderr line for any goal that runs tests (the forked surefire JVM's own "OpenJDK ...
    Sharing is only supported..." startup notice). Under 'Stop' that line gets promoted from
    harmless noise into a terminating error partway through the build, which looks exactly
    like a real failure and can abort a `package` run right after a genuine 0-violation
    spotbugs-check with no other symptom.
#>
$callerErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Stop'

try {
    $repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))

    # Same single source of truth bootstrap-jdk.ps1 reads, so the two can never disagree.
    $match = [regex]::Match((Get-Content -Raw (Join-Path $repoRoot 'pom.xml')), '<maven\.compiler\.release>\s*(\d+)\s*<')
    if (-not $match.Success) {
        throw "Could not read <maven.compiler.release> from $repoRoot\pom.xml - see bootstrap-jdk.ps1."
    }
    $jdkMajor = $match.Groups[1].Value
    $jdkHome = Join-Path $repoRoot ".tools\jdk-$jdkMajor"

    if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
        # Invoked with & rather than as a native command, so there is no meaningful
        # $LASTEXITCODE to inspect - it stays whatever an earlier native call left behind (or
        # $null). A genuine failure in bootstrap-jdk.ps1 throws, and its $ErrorActionPreference
        # = 'Stop' propagates that here; the post-condition below covers anything subtler.
        & (Join-Path $PSScriptRoot 'bootstrap-jdk.ps1')
        if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
            throw "bootstrap-jdk.ps1 finished but $jdkHome\bin\java.exe still does not exist."
        }
    }

    $env:JAVA_HOME = $jdkHome
    $jdkBin = Join-Path $jdkHome 'bin'
    # Guard against a repeated dot-source stacking duplicate entries onto PATH.
    if (($env:PATH -split ';') -notcontains $jdkBin) {
        $env:PATH = "$jdkBin;$env:PATH"
    }
} finally {
    $ErrorActionPreference = $callerErrorActionPreference
}
