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
