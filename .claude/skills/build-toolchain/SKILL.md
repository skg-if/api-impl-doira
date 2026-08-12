---
name: build-toolchain
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

## Running the build

```powershell
mvn clean test
```

## Regenerating golden JSON-LD fixtures

After an intentional change to `DataCiteToSkgIfMapper` (or anything else that changes
the response shape) - see README.md's Testing section for the full explanation:

```powershell
mvn test -Dtest=ProductsResourceTest,GrantsResourceTest -Dgolden.regenerate=true
git diff src/test/resources/expected/   # review before committing
```
