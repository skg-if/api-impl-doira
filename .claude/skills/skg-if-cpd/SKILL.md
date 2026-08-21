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

Prefer CSV over the default XML - it's ~30x smaller (around 1KB vs ~30KB for this repo's current
duplication count) because it drops the full scanned-file listing and the duplicated-code text
that XML embeds in every block, keeping just the numbers and locations:

```powershell
$dest = "<repo-root>\.tools"
$env:JAVA_HOME = "$dest\jdk-21"
$env:PATH = "$dest\jdk-21\bin;$dest\apache-maven-3.9.16\bin;$env:PATH"
mvn -q -B pmd:cpd "-Dformat=csv"
```

Always exits 0 regardless of what it finds. The report lands at `target/cpd.csv` (confirmed by
running it in this repo - `targetDirectory` defaults to `project.build.directory`, i.e.
`target/`, not `target/reports/` where the HTML copy goes for site generation). **`target/cpd.xml`
is written too, every time, regardless of `-Dformat`** - confirmed empirically (same timestamp,
every run) - it's not something `-Dformat=csv` avoids paying for at the `mvn` level, just a file
you don't need to open afterward.

The plugin-level `<configuration>` on the existing `pmd-check` execution in `pom.xml`
(`compileSourceRoots`/`testSourceRoots`/`excludeRoots` for the generated OpenAPI sources under
`target/generated-sources/`, `includeTests=true`) applies as defaults to `cpd`/`cpd-check` too -
confirmed empirically: the report's paths cover both `src/main/java` and `src/test/java`, and none
point into `target/generated-sources/`.

## Reading the report

`target/cpd.csv` is small enough to read in full directly - no grep-first ceremony needed. One
header row, then one row per duplication:

```
lines,tokens,occurrences
30,255,2,109,C:\...\CrossrefVenueMetadataXmlParser.java,174,C:\...\MedraOnixXmlParser.java
```

`lines`/`tokens` give the duplication's size (same meaning as XML's `<duplication lines=""
tokens="">` attributes), `occurrences` is how many places it appears, followed by that many
`<line>,<file>` pairs - one per occurrence.

Unlike XML, CSV has no embedded code snippet - if you need to see the actual duplicated text, Read
the source file directly at `<line>` (through roughly `<line> + lines - 1`) for one of the listed
occurrences, rather than opening `target/cpd.xml` for its `<codefragment>`.

If you do need the XML version for some other reason (e.g. the embedded snippet without opening
source files, or scripting against a stricter schema), it's already sitting at `target/cpd.xml`
from the same run - grep for `<duplication` first per the repo's "grep before reading large files"
convention, since it's much larger:

```powershell
Select-String -Path target\cpd.xml -Pattern '<duplication'
```

**For a report that names the duplicated *methods*, prefer CSV + targeted source reads over
XML anyway** - checked empirically against this repo's current 5 duplications: only 2 of the 5
`<codefragment>` blocks happen to start right at a method signature; the other 3 start mid-body
(one begins at an `@ExampleObject(...)` parameter annotation), so the enclosing method's name
isn't in the fragment at all and opening the real source file is still required. Since that
source lookup is usually unavoidable regardless of format, and XML's per-occurrence attributes
(`begintoken`/`column`/`endcolumn`/`endline`/`endtoken`/`line`/`path`, times two occurrences, times
every duplication) plus the full duplicated-code CDATA cost noticeably more tokens than the CSV's
one compact row per duplication, read the CSV first and open just the handful of distinct files it
names (grouped by file, not by duplication, since the same file often recurs across duplications)
to read off the real method signature.

## Tuning sensitivity

Confirmed via `mvn help:describe -Dplugin=org.apache.maven.plugins:maven-pmd-plugin:3.26.0
-Dgoal=cpd -Ddetail` against this repo's exact plugin version (don't guess at property names -
`maven-cpd-plugin`'s standalone `-Dminimum-tokens` is a different, unrelated plugin):

- `-DminimumTokens=<n>` - user property `minimumTokens`, default `100`. Lower it to catch smaller
  duplicated blocks, raise it to reduce noise.
- `-Dcpd.skip=true` - skip CPD entirely for one invocation.
- `-Dformat=<xml|csv|txt>` - which *additional* report format(s) to render; `target/cpd.xml`
  itself is always written regardless of this flag (`cpd-check`'s failure message always names
  `cpd.xml` specifically, even when invoked with `-Dformat=csv` - confirmed empirically).

## Known limitation

Unlike `pmd:check`, CPD has no inline suppression mechanism (no `@SuppressWarnings`-style
annotation) - intentional duplication (e.g. repeated test-fixture boilerplate) can only be
silenced via `excludes`/`excludeRoots` in `pom.xml`, `excludeFromFailureFile`, or by accepting the
violation and not gating the build on `cpd-check`.
