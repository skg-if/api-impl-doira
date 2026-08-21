---
name: skg-if-cpd
description: Run Maven PMD's CPD (Copy/Paste Detector) to find or gate on duplicated Java code in this repo (puma-skg-if-api), without a full compile/test cycle. Use whenever the user asks to check for duplicate/copy-pasted code, run CPD, or look for code duplication - including before a commit or when reviewing a diff that repeats existing logic.
---

# Maven CPD (copy/paste detection)

`maven-pmd-plugin` 3.26.0 is already declared in [pom.xml](../../../pom.xml) (~line 244), but the
plugin's only bound execution is the `check` goal (PMD rule violations) on `process-sources`.
That same plugin also ships CPD's mojos - `pmd:cpd` (report) and `pmd:cpd-check` (report + fail
the build on duplication) - but **neither is bound to any lifecycle phase**, so `mvn test`/
`package` never runs CPD; both goals must always be invoked explicitly. Don't confuse `pmd:check`
(rule violations) with `pmd:cpd`/`pmd:cpd-check` (duplication) - they're different goals from the
same plugin.

This needs the same portable JDK 21 / Maven 3.9 toolchain as any other `mvn` command in this
repo - see the `skg-if-build-toolchain` skill for one-time setup.

## Checking for duplication (fails the build on violation)

```powershell
$dest = "<repo-root>\.tools"
$env:JAVA_HOME = "$dest\jdk-21"
$env:PATH = "$dest\jdk-21\bin;$dest\apache-maven-3.9.16\bin;$env:PATH"
mvn -q -B pmd:cpd-check
```

Exit 0 means no duplication at or above the threshold (default 100 tokens, see "Tuning" below) -
report that and move on, no need to open any report file. A non-zero exit prints
`CPD <version> has found N duplications. For more details see: <repo>\target\cpd.xml` - open
`target/cpd.xml` at that point (see "Reading the report" below).

**Known baseline**: as of this writing, running this against `main` finds 10 pre-existing
duplications (all just above the 100-token default threshold, in mapper/DTO code) - a failure
here is not necessarily caused by whatever change is under review. Check whether the duplication
blocks reported in `target/cpd.xml` involve files touched by the current diff before treating a
failure as a regression.

## Generating a report without failing the build

```powershell
$dest = "<repo-root>\.tools"
$env:JAVA_HOME = "$dest\jdk-21"
$env:PATH = "$dest\jdk-21\bin;$dest\apache-maven-3.9.16\bin;$env:PATH"
mvn -q -B pmd:cpd
```

Always exits 0 regardless of what it finds. The XML report lands at `target/cpd.xml` (confirmed
by running it in this repo - `targetDirectory` defaults to `project.build.directory`, i.e.
`target/`, not `target/reports/` where the HTML copy goes for site generation).

The plugin-level `<configuration>` on the existing `pmd-check` execution in `pom.xml`
(`compileSourceRoots`/`testSourceRoots`/`excludeRoots` for the generated OpenAPI sources under
`target/generated-sources/`, `includeTests=true`) applies as defaults to `cpd`/`cpd-check` too -
confirmed empirically: `target/cpd.xml` paths cover both `src/main/java` and `src/test/java`, and
none point into `target/generated-sources/`.

## Reading the report

`target/cpd.xml` can be large - grep for `<duplication` first (each match's `lines="N"
tokens="M"` attributes tell you the size) rather than reading the whole file, per the repo's
"grep before reading large files" convention:

```powershell
Select-String -Path target\cpd.xml -Pattern '<duplication'
```

Then read just the `<duplication>...</duplication>` block(s) that matter (each contains one
`<file path="..." line="..."/>` entry per duplicate occurrence, followed by the shared
`<codefragment>`).

## Tuning sensitivity

Confirmed via `mvn help:describe -Dplugin=org.apache.maven.plugins:maven-pmd-plugin:3.26.0
-Dgoal=cpd -Ddetail` against this repo's exact plugin version (don't guess at property names -
`maven-cpd-plugin`'s standalone `-Dminimum-tokens` is a different, unrelated plugin):

- `-DminimumTokens=<n>` - user property `minimumTokens`, default `100`. Lower it to catch smaller
  duplicated blocks, raise it to reduce noise.
- `-Dcpd.skip=true` - skip CPD entirely for one invocation.
- `-Dformat=<xml|csv|txt>` - report format; XML is always produced too since `cpd-check` needs it.

## Known limitation

Unlike `pmd:check`, CPD has no inline suppression mechanism (no `@SuppressWarnings`-style
annotation) - intentional duplication (e.g. repeated test-fixture boilerplate) can only be
silenced via `excludes`/`excludeRoots` in `pom.xml`, `excludeFromFailureFile`, or by accepting the
violation and not gating the build on `cpd-check`.
