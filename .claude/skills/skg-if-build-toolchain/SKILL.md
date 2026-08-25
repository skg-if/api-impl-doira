---
name: skg-if-build-toolchain
description: Set up and use the portable JDK / Maven toolchain needed to build or test this project (mvn clean test, regenerating golden JSON-LD fixtures, etc). Use whenever a task requires running mvn or java in this repo.
---

# Portable JDK / Maven toolchain

Nothing here uses a system-wide `mvn`. Two mechanisms cover the toolchain, and **neither
pins a version in this file** - that is the whole point of the split:

- **Maven** comes from the `./mvnw` wrapper already committed at the repo root. It pins its
  own version in `.mvn/wrapper/maven-wrapper.properties` and caches the distribution under
  `~/.m2/wrapper/dists`. CI invokes the same wrapper, so local and CI builds run identical
  Maven. Always call `.\mvnw.cmd` (PowerShell) or `./mvnw` (Bash) - never a bare `mvn`. This
  half is already platform-independent: `mvnw` is committed executable with LF endings, so it
  runs as-is on a Linux clone.
- **The JDK** is resolved by the activation script, in this order:
  1. a portable Temurin build cached in `.tools/jdk-<major>` at the repo root (gitignored);
  2. otherwise the `java` already on `PATH`, **if** its feature version is at least the
     required one - this is the devcontainer/CI path, and it downloads nothing;
  3. otherwise a fresh download into `.tools/`, via
     [`bootstrap-jdk.ps1`](bootstrap-jdk.ps1) on Windows or
     [`bootstrap-jdk.sh`](bootstrap-jdk.sh) on Linux.

  The required feature version is read from `pom.xml`'s `<maven.compiler.release>`, the
  single source of truth for this repo's Java version, so bumping Java means editing
  `pom.xml` and nothing else *in the toolchain* - the activation and bootstrap scripts and
  `.github/actions/setup-java-from-pom` all derive it, and re-provision automatically.

  Three pins outside the toolchain still need hand-editing on a bump, because an image tag
  and README prose resolve before any script could run: `src/main/docker/Dockerfile.jvm`,
  `.devcontainer/devcontainer.json`, and `README.md`. `ToolchainVersionConsistencyTest`
  fails the build until they agree, so it will tell you exactly which are stale - but check
  the new tag actually exists upstream first. The two image lines version independently of
  Java (Red Hat's UBI stream suffix, and Microsoft's devcontainer image major, whose
  `1-<java>` line stopped at Java 21), so neither tag can be built by string-substituting
  the old major.

**On the Windows dev box specifically, there is no system-wide JDK or Maven and one should
not be installed** (`winget`/`choco`) - step 1 or 3 above always applies there, and tooling
stays scoped to this project rather than the user's machine. That constraint is about this
machine, not about the repo: see [Linux/macOS](#linuxmacos) below, where a distro-packaged
JDK is a perfectly good answer and step 2 will pick it up.

Caching the JDK in-repo (rather than in the session scratchpad) means it survives across
conversations and only downloads once per checkout.

## Activating the toolchain

There is no separate one-time setup step. Activation provisions the JDK if it is missing and
is a cheap no-op once it is there, so run it before every command that needs `java`/`mvnw`:

```powershell
. .\.claude\skills\skg-if-build-toolchain\activate.ps1
.\mvnw.cmd -version
```

```bash
source .claude/skills/skg-if-build-toolchain/activate.sh
./mvnw -version
```

**Dot-source it (`.` in PowerShell) or `source` it - never execute it.** A script run as a
child process cannot mutate its caller's environment, so `.\activate.ps1` or `./activate.sh`
appears to succeed while leaving `JAVA_HOME`/`PATH` untouched, and the `mvnw` call that
follows then fails for a reason that looks nothing like the actual cause.

**Never pipe or redirect the `source` line either** - not even to trim its output. Bash runs
every stage of a pipeline in a subshell, so `source .../activate.sh | tail -5` sources the
script into a child process that exits immediately, discarding the exports exactly like
executing it would. The symptom is a bare `java: command not found` on the *next* command in the
same call, which reads like a missing/failed JDK provision rather than a lost environment. Source
it on its own line and let it print what it prints:

```bash
source .claude/skills/skg-if-build-toolchain/activate.sh && java -version   # right
source .claude/skills/skg-if-build-toolchain/activate.sh | tail -5          # WRONG - exports lost
```

Each Bash/PowerShell tool call starts a fresh process, so re-activate in **every** invocation
that needs the toolchain. Nothing is persisted to the user or machine environment, and no
attempt should be made to persist it. Forgetting this in a later call doesn't look like a
skipped activation - `mvnw`/`java` fail with `The JAVA_HOME environment variable is not defined
correctly, this environment variable is needed to run this program.`, which reads like the
toolchain broke rather than "this call never activated it."

### Reading the examples in Bash

Every other example in this skill - and in the `skg-if-cpd`, `skg-if-format` and
`skg-if-openrewrite-refactor` skills, which point here - is written PowerShell-first. Two
substitutions turn any of them into its Bash equivalent, and they are the only two:

| PowerShell | Bash |
| ------------ | ------ |
| `. .\.claude\skills\skg-if-build-toolchain\activate.ps1` | `source .claude/skills/skg-if-build-toolchain/activate.sh` |
| `.\mvnw.cmd` | `./mvnw` |

Everything after `mvnw` (goals, `-q`/`-B`, `-Dtest=...`) is identical, since those are
arguments to Maven rather than shell syntax. The genuine shell differences are called out
individually where they arise - see the `*>` versus `>` note under "Large failures" below, and
the PowerShell-only quoting rules under "Regenerating golden JSON-LD fixtures". Both fences are
spelled out in full above only because dot-sourcing is the one step where getting the shell
wrong fails silently.

### Linux/macOS

`activate.sh` is the entry point on every platform (`activate.ps1` is Windows-only - its
backslash paths aren't separators under `pwsh` on Linux). The resolution order at the top of
this file is what makes a Linux clone work:

- **A distro/SDKMAN JDK on `PATH` is preferred over downloading**, as long as its feature
  version is at least `<maven.compiler.release>`. Nothing is exported in that case - the
  ambient `JAVA_HOME`/`PATH` are already right. A *too old* ambient JDK is rejected outright
  rather than used, because the alternative is a `invalid target release` error from Maven much
  later that points nowhere near the JDK.
- **Linux provisioning exists** ([`bootstrap-jdk.sh`](bootstrap-jdk.sh), x64 and aarch64) for a
  box with no suitable JDK. It is invoked as `bash <path>`, never `./<path>`, so it needs no
  executable bit.
- **macOS provisioning does not exist** - the Adoptium tarball nests the JDK under
  `Contents/Home`, so the script refuses rather than producing a `JAVA_HOME` that looks right
  and isn't. Install a JDK yourself (Homebrew, SDKMAN) and step 2 picks it up.
- **The devcontainer** ([`.devcontainer/devcontainer.json`](../../../.devcontainer/devcontainer.json))
  is the sanctioned container path; its image already ships a matching JDK, so activation there
  is a no-op that downloads nothing.

Not verified end-to-end: no CI job runs on Linux, so the actual Adoptium Linux download has
never been exercised. Treat a failure in it as plausible rather than surprising.

### Run from the repo root

Every command in this skill is written relative to the repo root - both the `activate` path and
`mvnw` itself - and Maven additionally needs the root as its working directory to find
`pom.xml`. The Bash and PowerShell tools **keep their working directory between calls**, so an
earlier `cd` into a subdirectory silently invalidates all of it. Make it unconditional rather
than assumed:

```powershell
Set-Location (git rev-parse --show-toplevel)
```

```bash
cd "$(git rev-parse --show-toplevel)"
```

Worth doing even when you believe you're already there, because the failure mode gives no hint
at the real cause: `.\activate.ps1` / `./mvnw` reports only a bare "is not recognized" or "No
such file or directory", which reads like a missing or broken toolchain rather than a wrong
working directory.

**Windows/git-bash only:** `activate.sh` also handles the MSYS drive-letter conversion a
Windows path needs before it can go into `PATH`; the reasoning is documented in the script
itself. Don't hand-build `JAVA_HOME`/`PATH` in Bash - source the script. Note `git rev-parse`
prints drive-letter form (`C:/...`), which is fine as a filesystem path but must never be
spliced into a Bash `PATH` for exactly that reason. On Linux/macOS none of this applies - paths
have no drive letter, and the plain `cd "$(git rev-parse --show-toplevel)"` above is all that's
needed.

## Checking for compile errors only

When the goal is just "does this change compile" - e.g. right after editing a
`.java` file, before writing/running any tests - don't run the `test` goal at all.
`compile`/`test-compile` skip surefire entirely, so the only output is javac
diagnostics:

```powershell
.\mvnw.cmd -q -B compile         # main sources only
.\mvnw.cmd -q -B test-compile    # also compiles test sources, still doesn't run them
```

This is both faster and far smaller than `clean test` for a step that's only
checking for typos/type errors, and it means a failing compile doesn't also pay
for a full surefire run that can't succeed anyway.

## Running the build

Full suite:

```powershell
. .\.claude\skills\skg-if-build-toolchain\activate.ps1
.\mvnw.cmd -q -B clean test
```

`-q` (quiet) drops plugin/download log noise; `-B` (batch mode) drops the
interactive progress-bar/ANSI noise. Maven still prints a `BUILD FAILURE` line and
the list of failing tests through `-q` - this doesn't hide real failures.

Reserve `clean` for a final verification pass. While iterating, drop it - an
incremental `.\mvnw.cmd -q -B test` reuses already-compiled classes and doesn't reprint
compiler/plugin setup output for files that haven't changed.

### Trust the exit code

A `0` exit from `mvnw` (package/test/`pmd:check`/`checkstyle:check`/etc.) is
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
.\mvnw.cmd -q -B test -Dtest=CrossrefToSkgIfMapperTest
```

Naming more than one class at once (e.g. to run every test class touched by a
multi-file change)? Quote the whole `-Dtest=` value:

```powershell
.\mvnw.cmd -q -B test "-Dtest=CrossrefToSkgIfMapperTest,DataCiteToSkgIfMapperTest"
```

See the quoting note under "Regenerating golden JSON-LD fixtures" below for why -
it applies to any multi-class `-Dtest=A,B`, not just that one command.

### Large failures: redirect to a file and grep before reading

`-q` keeps a *passing* build's console output small, but a *failing* one still
streams every stack trace straight into the command's captured output - which is
read in full every time. For a build/test run that might fail with a lot of
output (many broken tests, a cascading compile error), redirect to a log file and
search it instead of capturing everything directly.

**Always write the log outside `target/`** - the session scratchpad dir, or the
repo root - never a path under `target/` itself, `clean` or not. It costs
nothing to get in this habit, and it structurally rules out the failure mode
below rather than relying on remembering an exception for the `clean` case:

```powershell
.\mvnw.cmd -q -B test *> build.log
Select-String -Path build.log -Pattern 'ERROR|BUILD FAILURE|FAILED|Tests run:.*Failures: [1-9]|Tests run:.*Errors: [1-9]'
```

**`*>` and `Select-String` are PowerShell-only** - `*>` is PowerShell's
redirect-all-streams operator, not a shell-agnostic idiom. If running this via
the Bash tool instead, `*` is a glob that expands to every filename in the
current directory before `mvnw` even sees it (garbage arguments, confusing
"Unknown lifecycle phase" errors that name an unrelated file in the repo root)
and `Select-String` doesn't exist. Use the Bash equivalent instead:

```bash
./mvnw -q -B test > build.log 2>&1
grep -nE 'ERROR|BUILD FAILURE|FAILED|Tests run:.*Failures: [1-9]|Tests run:.*Errors: [1-9]' build.log
```

(`build.log` here lands in the repo root, since `mvnw` already runs from there;
the session scratchpad directory works too - just never a path under `target/`).
Only `Get-Content` the log in full (or open the specific
`.txt`/`.xml` report below) once the matched lines identify which class/line
needs the full trace. On a clean build this adds one extra command but costs
nothing; on a large failure it avoids paying for output that's mostly
irrelevant duplication.

**Never use `2>&1 | <cmdlet>` against `.\mvnw.cmd` in PowerShell**, even for a quick filter
instead of a full log file - use the `*> build.log` file-redirect pattern above instead.
`mvnw.cmd`'s forked test JVM writes a benign startup warning to stderr on every run (success or
not, e.g. `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes
because bootstrap classpath has been appended`). PowerShell 5.1's `2>&1` merges stderr into the
success stream by wrapping each line in a `NativeCommandError`, which makes the tool report
"Exit code 1" even when `mvnw` itself exited `0` and the build genuinely passed - `*>` to a real
file doesn't have this problem because it writes the streams directly rather than merging them
into the pipeline.

**Why it matters specifically when the command includes `clean`:** `clean`
deletes the entire `target/` directory early in the run, including a log file
under `target/` the shell already has open for writing - and each OS then fails
in a different, equally-confusing direction. On Unix-like shells (git-bash) the
process keeps writing to the now-unlinked file and `mvnw` still exits `0`, but
the directory entry is gone once `clean` finishes recreating `target/`: the
build genuinely passed, yet a later `tail`/`Get-Content` on that same path
fails with "No such file or directory". On PowerShell, Windows instead keeps a
lock on the still-open file, so `maven-clean-plugin` can't delete it and the
build **fails outright** with `[ERROR] Failed to execute goal
...:maven-clean-plugin:...:clean (default-clean) ... Failed to delete
C:\...\target\build.log -> [Help 1]` - a real non-zero exit that reads exactly
like an unrelated build/PMD/test problem, when the actual code under test was
never even reached. Writing the log outside `target/` (as above) avoids both
failure modes entirely - there is no exception case to remember.

### Reading results afterward - use the `.txt` reports, not the `.xml` ones

Every run writes two report files per test class under `target/surefire-reports/`:
a plain-text summary (`<FQCN>.txt`) and a JUnit XML report (`TEST-<FQCN>.xml`). The
XML version duplicates the full captured stdout/stack traces and is enormous by
comparison - e.g. `DataCiteProductsResourceTest`'s `.txt` summary is ~330 bytes; its `.xml`
report for the exact same run is ~60KB. Reading the `.xml` version by default costs
~180x more for no extra information in the common case. Read (or grep across) the
`.txt` files first:

```powershell
Get-Content target\surefire-reports\org.skgif.doi.rest.datacite.DataCiteProductsResourceTest.txt
```

Only open the matching `.xml` report when a `.txt` failure needs the full stack
trace the summary truncated.

## Regenerating golden JSON-LD fixtures

After an intentional change to `DataCiteToSkgIfMapper` (or anything else that changes
the response shape) - see README.md's Testing section for the full explanation:

```powershell
. .\.claude\skills\skg-if-build-toolchain\activate.ps1
.\mvnw.cmd test "-Dtest=ProductsGoldenTest,GrantsGoldenTest" "-Dgolden.regenerate=true"
git diff src/test/resources/expected/   # review before committing
```

Quote any `-Dtest=A,B` value with more than one class name, and quote any
`-D<property.name>=value` where the property name itself contains a dot (like
`-Dgolden.regenerate=true`) - PowerShell's parser can mis-split either shape
before `mvnw` ever runs (`ParserError: Missing argument in parameter list` for
the comma case, or an unrelated-looking `Unknown lifecycle phase
".regenerate=true"` from `mvnw` itself for the dot case), even though each is a
single native-command argument once quoted. A single `-Dtest=OneClass` (no
comma) or a dot-free property (like `-Dskip=true`) needs no quoting.
