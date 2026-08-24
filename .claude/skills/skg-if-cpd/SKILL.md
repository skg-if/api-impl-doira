---
name: skg-if-cpd
description: Run Maven PMD's CPD (Copy/Paste Detector) to find or gate on duplicated Java code in this repo (puma-skg-if-api), without a full compile/test cycle. Use whenever the user asks to check for duplicate/copy-pasted code, run CPD, or look for code duplication - including before a commit or when reviewing a diff that repeats existing logic.
---

# Maven CPD (copy/paste detection)

`maven-pmd-plugin` 3.28.0 is already declared in [pom.xml](../../../pom.xml), but the
plugin's only bound execution is the `check` goal (PMD rule violations) on `process-classes`
(deliberately after `compile`, so PMD gets `target/classes` on its auxclasspath - see the comment
on that execution, and "Measure from a clean tree" below).
That same plugin also ships CPD's mojos - `pmd:cpd` (report) and `pmd:cpd-check` (report + fail
the build on duplication) - but **neither is bound to any lifecycle phase**, so `.\mvnw.cmd test`/
`package` never runs CPD; both goals must always be invoked explicitly. Don't confuse `pmd:check`
(rule violations) with `pmd:cpd`/`pmd:cpd-check` (duplication) - they're different goals from the
same plugin.

This needs the same portable toolchain as any other build command in this repo. The
`activate.ps1` line in each snippet below provisions the JDK if it is missing and puts it on
PATH, so there is no separate setup step to run first - see the `skg-if-build-toolchain` skill
for how it resolves versions. Every snippet below is PowerShell; for the two substitutions that
turn it into the Bash equivalent, see
[Reading the examples in Bash](../skg-if-build-toolchain/SKILL.md#reading-the-examples-in-bash).

## Measure from a clean tree, or don't compare the result to CI

Both PMD's rule analysis and CPD read whatever is already sitting in `target/`, so a local run on
a working tree that has built before is not the same measurement CI makes on a fresh checkout.
This has bitten once for real: `pmd:check` exited 0 locally on sources where CI reported
`PMD 7.17.0 has found 3 violations`, because leftover `target/classes` from an earlier build gave
PMD the auxclasspath it needs for type resolution - which the old `process-sources` binding never
provided on CI (see [ISSUES.md](../ISSUES.md)). The binding is fixed, but the general rule stands:

- To reproduce or refute a CI finding, run `.\mvnw.cmd -B clean` first, or check the commit out
  into a throwaway `git worktree` and run there.
- A clean exit on a dirty tree is evidence about *your tree*, not about CI. Say which one you
  measured when reporting a result.

## Checking for duplication (fails the build on violation)

```powershell
. .\.claude\skills\skg-if-build-toolchain\activate.ps1
.\mvnw.cmd -q -B pmd:cpd-check
```

Exit 0 means no duplication at or above the threshold (default 100 tokens, see "Tuning" below) -
report that and move on, no need to open any report file. A non-zero exit prints
`CPD <version> has found N duplications. For more details see: <repo>\target\cpd.xml` - open
`target/cpd.xml` at that point (see "Reading the report" below).

**Known baseline**: `main` is clean at the default threshold - re-measured under
maven-pmd-plugin 3.28.0 / PMD 7.17.0, which reports **0 duplications** across the 169 scanned
files (PMD 7.7.0 reported 10 blocks in mapper/DTO code on similar sources, so do not carry an
older non-zero baseline forward). A failure is therefore a signal about the change under review,
not pre-existing noise - but still check whether the blocks in `target/cpd.xml` involve files the
current diff touched before calling it a regression. Lowering the threshold does surface blocks
(`-DminimumTokens=60` finds 20), so a non-default threshold has no baseline of its own.

## Generating a report without failing the build

Prefer CSV over the default XML - it is far smaller (measured here: 26 bytes vs 25KB with no
duplication above the threshold) because it drops the scanned-file listing that XML emits for all
169 files regardless of findings, plus the duplicated-code text XML embeds in every block, keeping
just the numbers and locations:

```powershell
. .\.claude\skills\skg-if-build-toolchain\activate.ps1
.\mvnw.cmd -q -B pmd:cpd "-Dformat=csv"
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
XML anyway** - checked empirically back when this repo did report duplications above the default
threshold: only 2 of the 5 `<codefragment>` blocks then present started right at a method
signature; the other 3 started mid-body (one at an `@ExampleObject(...)` parameter annotation), so
the enclosing method name was not in the fragment at all and opening the real source file was
still required. Since that source lookup is usually unavoidable regardless of format, and XML's
per-occurrence attributes (`begintoken`/`column`/`endcolumn`/`endline`/`endtoken`/`line`/`path`,
times two occurrences, times every duplication) plus the full duplicated-code CDATA cost
noticeably more tokens than the CSV's one compact row per duplication, read the CSV first and
open just the handful of distinct files it names (grouped by file, not by duplication, since the
same file often recurs across duplications) to read off the real method signature.

## Tuning sensitivity

Confirmed via `.\mvnw.cmd help:describe -Dplugin=org.apache.maven.plugins:maven-pmd-plugin:3.28.0
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
