---
name: skg-if-build-toolchain
description: Set up and use the portable JDK 21 / Maven 3.9 toolchain needed to build or test this project (mvn clean test, regenerating golden JSON-LD fixtures, etc). Use whenever a task requires running mvn or java in this repo.
---

# Portable JDK 21 / Maven toolchain

`pom.xml` targets `maven.compiler.release=21`, which requires JDK 21+ and Maven 3.9+.
There is no system-wide Maven/JDK on this machine, and one should not be installed
(`winget`/`choco`). Instead use a **portable, self-contained** toolchain cached in
`.tools/` at the repo root (gitignored) - never system-wide, never persisted to
user/machine environment variables.

Caching it in-repo (instead of the session scratchpad) means it survives across
conversations and only needs downloading once per checkout.

## One-time setup (per checkout)

Skip this entirely if `.tools\jdk-21\bin\java.exe` and
`.tools\apache-maven-3.9.16\bin\mvn.cmd` already exist - go straight to "Invoking
java/mvn" below.

```powershell
$dest = "<repo-root>\.tools"
New-Item -ItemType Directory -Force $dest | Out-Null

# Temurin 21 JDK, Windows x64 - stable "always latest" redirect, no build number to go stale
Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile "$dest\temurin21.zip"
Expand-Archive -Path "$dest\temurin21.zip" -DestinationPath "$dest\jdk-21-extracted"
# Adoptium zips contain one top-level jdk-21.x.x+y folder - move/rename it for a stable path:
Move-Item "$dest\jdk-21-extracted\jdk-21*" "$dest\jdk-21"
Remove-Item "$dest\temurin21.zip", "$dest\jdk-21-extracted" -Recurse -Force

# Apache Maven (bump the version in the URL if a newer one is needed later)
Invoke-WebRequest -Uri "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip" -OutFile "$dest\maven.zip"
Expand-Archive -Path "$dest\maven.zip" -DestinationPath "$dest"
# produces $dest\apache-maven-3.9.16
Remove-Item "$dest\maven.zip" -Force
```

## Invoking `java`/`mvn` (scoped to one command, nothing persisted)

```powershell
$dest = "<repo-root>\.tools"
$env:JAVA_HOME = "$dest\jdk-21"
$env:PATH = "$dest\jdk-21\bin;$dest\apache-maven-3.9.16\bin;$env:PATH"
mvn -version   # confirm: Java 21, Maven 3.9.16
```

Set `JAVA_HOME`/`PATH` this way in every command/tool invocation that needs them (each
Bash/PowerShell tool call starts a fresh process) - do not attempt to persist them to
the user or machine environment.

## Checking for compile errors only

When the goal is just "does this change compile" - e.g. right after editing a
`.java` file, before writing/running any tests - don't run the `test` goal at all.
`compile`/`test-compile` skip surefire entirely, so the only output is javac
diagnostics:

```powershell
mvn -q -B compile         # main sources only
mvn -q -B test-compile    # also compiles test sources, still doesn't run them
```

This is both faster and far smaller than `clean test` for a step that's only
checking for typos/type errors, and it means a failing compile doesn't also pay
for a full surefire run that can't succeed anyway.

## Running the build

Full suite:

```powershell
mvn -q -B clean test
```

`-q` (quiet) drops plugin/download log noise; `-B` (batch mode) drops the
interactive progress-bar/ANSI noise. Maven still prints a `BUILD FAILURE` line and
the list of failing tests through `-q` - this doesn't hide real failures.

Reserve `clean` for a final verification pass. While iterating, drop it - an
incremental `mvn -q -B test` reuses already-compiled classes and doesn't reprint
compiler/plugin setup output for files that haven't changed.

### Trust the exit code

A `0` exit from `mvn` (package/test/`pmd:check`/`checkstyle:check`/etc.) is
sufficient proof the goal ran and passed - report success and move on. This
applies to every verification plugin bound into the build (PMD, checkstyle,
surefire, ...), not just PMD specifically. Don't additionally open
`target/pmd.xml`, `target/checkstyle-result.xml`, `target/surefire-reports/*.xml`,
or other generated report files just to reconfirm what the exit code already
established, even under `-q` where per-file summary lines are suppressed. Only
dig into those report files when the exit code is non-zero (to find the failure),
or when the task itself asks for the report's content (e.g. counting violations).

When iterating on one class rather than validating the whole change, scope the run
instead of re-running everything - reach for this by default whenever the change
is localized to one mapper/resource, not just when a full run already failed:

```powershell
mvn -q -B test -Dtest=CrossrefToSkgIfMapperTest
```

### Large failures: redirect to a file and grep before reading

`-q` keeps a *passing* build's console output small, but a *failing* one still
streams every stack trace straight into the command's captured output - which is
read in full every time. For a build/test run that might fail with a lot of
output (many broken tests, a cascading compile error), redirect to a log file and
search it instead of capturing everything directly:

```powershell
mvn -q -B test *> target\build.log
Select-String -Path target\build.log -Pattern 'ERROR|BUILD FAILURE|FAILED|Tests run:.*Failures: [1-9]|Tests run:.*Errors: [1-9]'
```

Only `Get-Content target\build.log` in full (or open the specific `.txt`/`.xml`
report below) once the matched lines identify which class/line needs the full
trace. On a clean build this adds one extra command but costs nothing; on a
large failure it avoids paying for output that's mostly irrelevant duplication.

### Reading results afterward - use the `.txt` reports, not the `.xml` ones

Every run writes two report files per test class under `target/surefire-reports/`:
a plain-text summary (`<FQCN>.txt`) and a JUnit XML report (`TEST-<FQCN>.xml`). The
XML version duplicates the full captured stdout/stack traces and is enormous by
comparison - e.g. `DataCiteProductsResourceTest`'s `.txt` summary is ~330 bytes; its `.xml`
report for the exact same run is ~60KB. Reading the `.xml` version by default costs
~180x more for no extra information in the common case. Read (or grep across) the
`.txt` files first:

```powershell
Get-Content target\surefire-reports\org.skgif.doi.rest.DataCiteProductsResourceTest.txt
```

Only open the matching `.xml` report when a `.txt` failure needs the full stack
trace the summary truncated.

## Regenerating golden JSON-LD fixtures

After an intentional change to `DataCiteToSkgIfMapper` (or anything else that changes
the response shape) - see README.md's Testing section for the full explanation:

```powershell
mvn test -Dtest=DataCiteProductsResourceTest,DataCiteGrantsResourceTest -Dgolden.regenerate=true
git diff src/test/resources/expected/   # review before committing
```
