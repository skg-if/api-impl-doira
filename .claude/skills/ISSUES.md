# Skill issue log

A shared inbox for tool-run friction that traces back to a
`.claude/skills/*/SKILL.md` being incomplete, wrong, or ambiguous - one file
across all skills, not scattered per-skill sections, so it's easy to scan and
triage in one pass.

Agents log to this file **automatically**, without being asked - see the
"Log tool-run issues that reveal skill gaps" rule in [`CLAUDE.md`](../../CLAUDE.md).
This file only covers logging; whether the underlying `SKILL.md` gets patched
immediately (same session) or later (during a triage pass) is a separate call.

## When to add an entry

- A tool run failed, behaved confusingly, or wasted turns in a way that
  traces back to a skill's documented commands/instructions.
- You found a gap in a skill while working on something else and don't have
  time or certainty to patch the `SKILL.md` right now.
- You already diagnosed and fixed the `SKILL.md` in the same turn - log it
  anyway, marked `Fixed`, so the log doubles as a changelog of what's been
  hardened and why.

## Entry template

Copy this block per issue, newest entry on top:

```
## <short title>

- Date: YYYY-MM-DD
- Skill: `<skill-dir>/SKILL.md`
- Symptom: <the exact error/output that was confusing>
- Root cause: <diagnosis - why it happened, not just what happened>
- Fix: <what changed, and where - or "not yet fixed" if left Open>
- Status: Open | Fixed
```

## Triage workflow

When reviewing `Open` entries (on request, or opportunistically while already
touching a skill's domain): diagnose the root cause if not already recorded,
patch the relevant `SKILL.md` with the fix, then flip the entry's `Status` to
`Fixed` and note the fix. Don't delete resolved entries - they stay as a
record of what the skill used to get wrong.

---

## Moving a class out of a package can leave callers unable to see it at all - not just missing an import

- Date: 2026-08-21
- Skill: `skg-if-openrewrite-refactor/SKILL.md`
- Symptom: after `ChangeType`-moving ~11 package-private helper classes (`FilterQuerySyntax`,
  `JsonLdContextBase`, `JsonLdEnvelopes`, `JsonLdErrors`, `JsonLdLinks`, `JsonLdMeta`,
  `RequestPagination`, plus the moved `Resource`/`Filters` classes themselves) so that new
  `org.skgif.doi.rest.crossref`/`rest.datacite`/`rest.medra` subpackages could use them, `mvn
  compile` failed with a wave of `X is not public in Y; cannot be accessed from outside package`
  errors - a different failure shape than the "Known gotchas" section's documented
  missing-import case (`cannot find symbol: class X`), and not mentioned anywhere in the skill.
- Root cause: the skill's "Known gotchas" section only covers a moved/renamed class breaking
  *other classes' imports* of it - it assumes accessibility was never the issue, only whether an
  import statement is present. That assumption holds for a rename or a same-visibility move, but
  not for splitting one package into several: several classes that stayed behind
  (`FilterQuerySyntax` and friends) were package-private, which was fine when every caller lived
  in the same flat `org.skgif.doi.rest` package - once callers moved into `rest.crossref`/
  `rest.datacite`/`rest.medra` subpackages (a *different* Java package each, despite the dotted
  name suggesting nesting), package-private access no longer reached them at all. `ChangeType`
  has no way to know a member needs wider visibility - it only rewrites type references, never
  touches modifiers - so this is silent until `mvn compile test-compile` surfaces it, and each hit
  needs a judgment call (bump to `public`, and add Javadoc if the project's Checkstyle requires it
  on public members) rather than a mechanical fix.
- Fix: not yet patched into the skill. Should add a second bullet under "Known gotchas,
  confirmed by real verification runs in this repo" alongside the existing missing-import one:
  when a class move crosses a package boundary (most likely for a *package split*, e.g. flattening
  one package into per-provider/per-feature subpackages, rather than a simple move/rename),
  `mvn compile test-compile`'s failure mode can be `is not public in <pkg>; cannot be accessed
  from outside package` instead of `cannot find symbol` - treat that as "widen this member's (and
  possibly its declaring class's) visibility to `public`," and re-check whether the project's
  Checkstyle/PMD rules now require Javadoc on it now that it's public (this repo's
  `MissingJavadocMethod`/`JavadocVariable` default to `public` scope, so a previously-undocumented
  package-private method needs a Javadoc comment added at the same time it's widened).
- Status: Open

---

## `C:/...`-style JAVA_HOME/PATH silently breaks `mvn` resolution in Bash (git-bash)

- Date: 2026-08-21
- Skill: `skg-if-build-toolchain/SKILL.md`
- Symptom: inside a git worktree, a Bash command did
  `export JAVA_HOME="C:/Puma/git/puma-skg-if-api/.tools/jdk-21" && export
  PATH="C:/Puma/git/puma-skg-if-api/.tools/jdk-21/bin:C:/Puma/git/puma-skg-if-api/.tools/apache-maven-3.9.16/bin:$PATH"`
  then `mvn -v`, which failed with `/usr/bin/bash: line 8: mvn: command not
  found` (exit 127) - even though the toolchain was already set up and working
  fine from PowerShell in the same repo. Looked like the worktree itself was
  missing the toolchain or a symlink to it wasn't set up correctly.
- Root cause: reproduced directly (not worktree-specific at all - fails the
  same way from the main repo root). `:` is Bash's `PATH`-separator character.
  A Windows drive-letter path like `C:/Puma/git/.../bin` embeds a colon right
  after the drive letter, so Bash's `PATH`-splitting logic breaks that one
  intended entry into two garbage entries (`C` and `/Puma/git/...`, which is
  missing its drive root and doesn't exist) - `echo $PATH | tr ':' '\n'`
  confirmed the split. Neither fragment resolves to a real directory, so `mvn`
  is never found, and the resulting `command not found` gives no hint the
  actual problem is the path format rather than a missing/broken toolchain.
  The skill's "Invoking java/mvn" section only ever showed the PowerShell form
  (backslash + semicolon, e.g. `$dest\jdk-21\bin;...`), with no Bash-equivalent
  example to copy from - so constructing one from scratch defaulted to the
  same `C:/...` drive-letter notation that looks natural but silently breaks
  in Bash specifically because of the colon.
- Fix: added a Bash-specific example right under the existing PowerShell one
  in [`skg-if-build-toolchain/SKILL.md`](skg-if-build-toolchain/SKILL.md)'s
  "Invoking java/mvn" section, using MSYS-style `/c/...` paths (no colon after
  the drive letter), plus a paragraph explaining the colon/PATH-separator
  collision concretely so the failure is recognizable next time even if the
  example itself isn't at hand.
- Status: Fixed

---

## Multi-class `-Dtest=A,B` quoting fix from 2026-08-20 didn't prevent a repeat on 2026-08-21

- Date: 2026-08-21
- Skill: `skg-if-build-toolchain/SKILL.md`
- Symptom: after a multi-file boilerplate-extraction refactor, ran
  `mvn -q -B test -Dtest=CrossrefToSkgIfMapperDatesTest,CrossrefToSkgIfMapperGrantTest,...`
  (12 classes, unquoted) via the PowerShell tool to verify every affected test class at once.
  Failed before `mvn` even started: `ParserError: Missing argument in parameter list` /
  `FullyQualifiedErrorId: MissingArgument` - the exact same failure already logged and marked
  `Fixed` below on 2026-08-20 ("Unquoted multi-class `-Dtest=A,B` breaks the PowerShell parser").
- Root cause: the 2026-08-20 fix only quoted the `-Dtest=` value in the one example it lived
  next to - the "Regenerating golden JSON-LD fixtures" section's golden-regen command - and added
  the general quoting rule as prose underneath that unrelated example. The "scope the run"
  section higher up (where a multi-class `-Dtest=A,B` is actually most likely to get typed, e.g.
  verifying several classes touched by one refactor) still only showed a single-class example
  with no quoting note nearby, so the general rule never surfaced when constructing this
  particular command - despite technically being documented elsewhere in the same file.
- Fix: added a second example directly under the "scope the run" section's existing
  single-class example in
  [`skg-if-build-toolchain/SKILL.md`](skg-if-build-toolchain/SKILL.md), showing a quoted
  multi-class `-Dtest=A,B` and pointing at the existing general-rule prose instead of duplicating
  it, so the quoting reminder appears at the point of use rather than only under the unrelated
  golden-regen example.
- Status: Fixed

---

## `skg-if-format` claims Spotless doesn't run automatically, but it does

- Date: 2026-08-21
- Skill: `skg-if-format/SKILL.md`
- Symptom: while building/verifying the `openrewrite-refactor-toolbox` skill,
  a plain `mvn -q -B compile test-compile` (no spotless goal invoked
  explicitly) failed with `[ERROR] Failed to execute goal
  com.diffplug.spotless:spotless-maven-plugin:2.46.1:check
  (spotless-check)...` - contradicting `skg-if-format/SKILL.md`'s opening
  paragraph, which says the plugin's `process-sources` binding "is currently
  commented out in `pom.xml`... so right now Spotless does **not** run
  automatically on `mvn compile`/`test`/`package`."
- Root cause: `pom.xml` (around line 197-240) currently has an *active*
  `spotless-check` execution bound to `process-sources`, positioned before
  `pmd-check` and the checkstyle executions (also `process-sources`) via POM
  declaration order - the plugin's own inline comment explains this ordering
  is deliberate. The skill doc's claim that this binding is commented out is
  stale relative to the current `pom.xml` - the doc does say to "re-check
  `pom.xml` before relying on this if that comment is ever removed," but
  didn't catch that it already had been.
- Fix: not yet fixed in `skg-if-format/SKILL.md` itself (out of scope for the
  session that found it - it was authoring a different skill). Whoever next
  touches `skg-if-format` should update the opening paragraph to match
  current `pom.xml`, or reconsider whether to keep asserting the binding's
  state at all given it's already drifted once.
- Status: Open

---

## `*> build.log` / `Select-String` example runs in Bash, `*` glob-expands into `mvn`'s arguments

- Date: 2026-08-21
- Skill: `skg-if-build-toolchain/SKILL.md`
- Symptom: ran the skill's own redirect-to-a-file example via the Bash tool
  (not PowerShell): `mvn -q -B clean test *> build.log` failed immediately
  with `[ERROR] Unknown lifecycle phase "checkstyle.xml". You must specify a
  valid lifecycle phase or a goal...`. `checkstyle.xml` is an unrelated file
  that happens to sit in the repo root - nothing to do with the actual build.
  The follow-up grep (`grep -nE '...' build.log | head -100`) then found
  nothing new, since `build.log` was never written either.
- Root cause: the example was fenced ` ```powershell ` and used `*>`
  (PowerShell's redirect-all-streams operator) plus `Select-String`, both
  PowerShell-only. In Bash, `*` isn't a redirect operator at all - it's an
  unquoted glob that expands to every filename in the current directory
  (`build.log checkstyle.xml pom.xml ...`) before `mvn` runs, so `mvn` gets
  handed a pile of filenames as extra arguments and tries to interpret the
  first one as a lifecycle phase/goal. Since this environment offers both a
  Bash tool and a PowerShell tool, and the skill's other examples are also
  ```powershell-fenced without a Bash caveat, it's natural to copy this one
  into whichever shell is at hand without noticing the syntax is
  shell-specific.
- Fix: added a paragraph in
  [`skg-if-build-toolchain/SKILL.md`](skg-if-build-toolchain/SKILL.md) right
  after the `*>`/`Select-String` example calling out that both are
  PowerShell-only, explaining the glob-expansion failure mode concretely, and
  giving the Bash equivalent (`> build.log 2>&1` + `grep -nE`).
- Status: Fixed

---

## Reached for system `python3`/`jq` instead of the portable-python/jq-json skills

- Date: 2026-08-21
- Skill: `CLAUDE.md` (missing guidance, not a specific `SKILL.md`)
- Symptom: needed to inspect `descriptionType`/`lang` values across ~10 DataCite fixtures under
  `src/test/resources`. Ran `python3 -c "..."` directly - exit code 126 (no system Python on
  this machine, same constraint CLAUDE.md already documents for Maven/JDK). Then checked `which
  jq`/`jq --version` - also absent ("no jq"). Fell back to a manual `Grep -B/-A` scan instead,
  which worked but was slower and less precise than a proper JSON query would have been.
- Root cause: CLAUDE.md has a "No system Maven/JDK 21" section telling agents to use the
  `skg-if-build-toolchain` skill instead of guessing, but had no equivalent note for
  Python/jq - even though this repo has `portable-python` and `jq-json` skills built for exactly
  this ("even if you just say 'grab this field from the JSON'" per `jq-json`'s own
  description). Without a CLAUDE.md-level pointer, it's natural to try the obvious system
  command first and only discover the dedicated skill after already failing twice.
- Fix: added a "No system Python or jq either" section to `CLAUDE.md` right after the existing
  Maven/JDK one, pointing at `portable-python` and `jq-json` by name.
- Status: Fixed

## Golden-regen example cites test classes that no longer exist

- Date: 2026-08-21
- Skill: `skg-if-build-toolchain/SKILL.md` (also `README.md`'s Testing section, same content)
- Symptom: the "Regenerating golden JSON-LD fixtures" section's example command was
  `mvn test "-Dtest=DataCiteProductsResourceTest,DataCiteGrantsResourceTest" "-Dgolden.regenerate=true"`.
  `DataCiteProductsResourceTest` exists but is unrelated to golden regeneration;
  `DataCiteGrantsResourceTest`/`CrossrefProductsResourceTest`/`CrossrefGrantsResourceTest` don't
  exist at all. Caught only because `ProductsGoldenTest`'s own class javadoc gave the correct
  command, which was cross-checked against this skill/README before trusting either.
- Root cause: the golden-test classes were consolidated at some point into
  `ProductsGoldenTest` (covers DataCite + Crossref + mEDRA products) and `GrantsGoldenTest`
  (covers DataCite + Crossref grants), but the skill doc and README's Testing section were never
  updated to match - both still described the pre-consolidation per-provider-per-entity class
  names.
- Fix: updated both `skg-if-build-toolchain/SKILL.md` and `README.md` to
  `mvn test -Dtest=ProductsGoldenTest,GrantsGoldenTest -Dgolden.regenerate=true`, with a note that
  each class already covers every provider so there's no need for separate per-provider commands.
- Status: Fixed

## `mvn clean test *> target\build.log` fails outright on PowerShell (not just silently loses the log)

- Date: 2026-08-21
- Skill: `skg-if-build-toolchain/SKILL.md`
- Symptom: ran `mvn -q -B clean test *> C:\Puma\git\puma-skg-if-api\target\build-full.log`
  in PowerShell after finishing an unrelated code change. The command exited 1
  with `[ERROR] Failed to execute goal
  org.apache.maven.plugins:maven-clean-plugin:3.2.0:clean (default-clean) on
  project puma-skg-if-api: Failed to clean project: Failed to delete
  C:\Puma\git\puma-skg-if-api\target\build-full.log -> [Help 1]`. This looked
  like the build itself had failed, prompting a second full run before
  realizing the actual code was never even compiled/tested.
- Root cause: the skill already documents "never redirect into a path under
  `target/` when the command includes `clean`", but only describes the
  Unix/git-bash symptom (silent success, log file missing afterward). On
  PowerShell/Windows the same mistake produces the opposite-looking but
  equally misleading symptom: the OS holds a lock on the still-open log file,
  so `maven-clean-plugin` can't delete it and `mvn` fails outright with a
  `maven-clean-plugin`/"Failed to delete" error - which reads exactly like a
  real build/PMD/test problem worth debugging, wasting a full extra
  clean-test cycle (this repo's tests take several minutes) before the actual
  cause (the redirect path, not the code change) was identified.
- Fix: the original fix only added a PowerShell-specific paragraph describing
  the symptom - reactive, not preventive: it still required hitting the
  failure and recognizing it after the fact. Replaced that with a preventive
  fix in [`skg-if-build-toolchain/SKILL.md`](skg-if-build-toolchain/SKILL.md):
  the "Large failures: redirect to a file" section's own copyable example now
  always writes the log outside `target/` (e.g. `mvn -q -B test *>
  build.log` from the repo root) regardless of whether `clean` is in the
  command, instead of showing a `target\build.log` example plus a separate
  warning not to combine it with `clean`. Structurally ruling out ever
  redirecting into `target/` removes the exception to remember - the
  git-bash/PowerShell symptom explanation is kept, but only as the "why",
  not as something to still watch for.
- Status: Fixed

---

## Unquoted `-Dgolden.regenerate=true` breaks the PowerShell parser

- Date: 2026-08-20
- Skill: `skg-if-build-toolchain/SKILL.md`
- Symptom: `mvn -q -B test -Dtest=ProductsGoldenTest -Dgolden.regenerate=true`
  (following the skill's own "Regenerating golden JSON-LD fixtures" example,
  which itself leaves this value unquoted) failed before running any tests:
  `[ERROR] Unknown lifecycle phase ".regenerate=true". You must specify a
  valid lifecycle phase or a goal...`
- Root cause: the skill's existing quoting note only covered the
  comma-separated `-Dtest=A,B` case; it didn't call out that a `-D<property>`
  value whose property name itself contains a dot (`-Dgolden.regenerate=true`)
  can be similarly mis-split by PowerShell before it reaches `mvn` as one
  argument, and the section's own example command wasn't quoted, so following
  it literally hit the exact bug the neighboring note (partially) warned about.
- Fix: quoted `-Dgolden.regenerate=true` in the example in
  [`skg-if-build-toolchain/SKILL.md`](skg-if-build-toolchain/SKILL.md) and
  broadened the quoting note to cover any `-D` value containing a comma or a
  dot in the property name, not just multi-class `-Dtest=A,B`.
- Status: Fixed

---

## Unquoted multi-class `-Dtest=A,B` breaks the PowerShell parser

- Date: 2026-08-20
- Skill: `skg-if-build-toolchain/SKILL.md`
- Symptom: `mvn -q -B test -Dtest=DataCiteGrantFiltersTest,DataCiteProductFiltersTest`
  (copying the skill's own golden-fixture example shape) failed before `mvn` ran at
  all: `ParserError: Missing argument in parameter list` /
  `FullyQualifiedErrorId: MissingArgument`, pointing into the middle of the
  `-Dtest=...` argument.
- Root cause: PowerShell's tokenizer treats an unquoted comma in a bare argument as
  starting an array literal in some contexts, so `-Dtest=ClassA,ClassB` unquoted can
  fail to parse as one argument even though it's destined for a native exe (`mvn`)
  that would happily take it as a single string. The skill's own "Regenerating
  golden JSON-LD fixtures" example used exactly this unquoted comma-separated
  `-Dtest=` shape under a ` ```powershell ` block, so following it literally hit the
  same failure.
- Fix: quoted the `-Dtest=...` value in that example and added a note in
  [`skg-if-build-toolchain/SKILL.md`](skg-if-build-toolchain/SKILL.md) to always
  quote a multi-class `-Dtest=A,B` value in PowerShell (a single-class value needs
  no quoting).
- Status: Fixed

---

## `mvn clean test` redirected into `target/build.log` loses the log

- Date: 2026-08-20
- Skill: `skg-if-build-toolchain/SKILL.md`
- Symptom: a command like `mvn -q -B clean test > target/build.log 2>&1;
  echo "EXIT:$?"; tail target/build.log` printed `EXIT:0` (build passed) but
  then `tail: cannot open 'target/build.log' for reading: No such file or
  directory`.
- Root cause: `clean` deletes the entire `target/` directory early in the
  run, including the log file the shell already had open for writing. On
  git-bash the write keeps succeeding against the now-unlinked file (so
  `mvn` still exits `0`), but the directory entry is gone once `clean`
  finishes recreating `target/` - so a build that genuinely passed leaves
  behind no log file for a later `tail`/`Get-Content` to read.
- Fix: added a warning in the "Large failures: redirect to a file and grep
  before reading" section of
  [`skg-if-build-toolchain/SKILL.md`](skg-if-build-toolchain/SKILL.md) -
  never redirect a `clean`-including run into a path under `target/`; write
  the log outside `target/` instead, or run `clean` separately first.
- Status: Fixed
