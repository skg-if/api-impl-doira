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
