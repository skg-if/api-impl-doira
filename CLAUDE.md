# Notes for agents working in this repo

## No system Maven/JDK 21 on your machine

Building/testing this project
(`pom.xml` targets `maven.compiler.release=21`) requires JDK 21+ and Maven 3.9+.

Rather than a system-wide install (`winget`/`choco`), use a **portable, self-contained**
toolchain downloaded into your session's scratchpad directory - never system-wide, never
persisted to user/machine environment variables.

### One-time setup (per session)

```powershell
$dest = "<your-scratchpad-dir>"   # e.g. the Claude Code scratchpad for this session

# Temurin 21 JDK, Windows x64 - stable "always latest" redirect, no build number to go stale
Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile "$dest\temurin21.zip"
Expand-Archive -Path "$dest\temurin21.zip" -DestinationPath "$dest\jdk-21-extracted"
# Adoptium zips contain one top-level jdk-21.x.x+y folder - move/rename it for a stable path:
Move-Item "$dest\jdk-21-extracted\jdk-21*" "$dest\jdk-21"

# Apache Maven (bump the version in the URL if a newer one is needed later)
Invoke-WebRequest -Uri "https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip" -OutFile "$dest\maven.zip"
Expand-Archive -Path "$dest\maven.zip" -DestinationPath "$dest"
# produces $dest\apache-maven-3.9.16
```

### Invoking `java`/`mvn` (scoped to one command, nothing persisted)

```powershell
$env:JAVA_HOME = "<scratchpad>\jdk-21"
$env:PATH = "<scratchpad>\jdk-21\bin;<scratchpad>\apache-maven-3.9.16\bin;$env:PATH"
mvn -version   # confirm: Java 21, Maven 3.9.16
```

Set `JAVA_HOME`/`PATH` this way in every command/tool invocation that needs them (each
Bash/PowerShell tool call starts a fresh process) - do not attempt to persist them to the
user or machine environment.

### Running the build

```powershell
mvn clean test
```

To regenerate the golden JSON-LD files under `src/test/resources/expected/` after an
intentional change to `DataCiteToSkgIfMapper` (or anything else that changes the response
shape) - see README.md's Testing section for the full explanation:

```powershell
mvn test -Dtest=ProductsResourceTest,GrantsResourceTest -Dgolden.regenerate=true
git diff src/test/resources/expected/   # review before committing
```
