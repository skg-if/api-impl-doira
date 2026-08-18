---
name: skg-if-format
description: Run Maven Spotless to check or auto-fix Java formatting in this repo (puma-skg-if-api), without a full compile/test cycle. Use whenever the user asks to format/reformat Java code, fix formatting/style violations, run spotless, or check if the code is properly formatted - including right before a commit, after a large edit, or when a build fails on formatting/checkstyle drift.
---

# Maven Spotless formatting

`spotless-maven-plugin` (pom.xml) reformats this repo's Java sources against
`intellij-style.xml` and strips unused imports. Its lifecycle binding to
`process-sources` is currently commented out in `pom.xml` (see the comment above the
plugin block) while that style file's continuation-indent settings are being revised -
so right now Spotless does **not** run automatically on `mvn compile`/`test`/`package`.
Run it on demand with the goals below. CLAUDE.md's "Java code style" section describes
the rules Spotless enforces; re-check `pom.xml` before relying on this if that comment
is ever removed, since it means the behavior described here has reverted to automatic.

This needs the same portable JDK 21 / Maven 3.9 toolchain as any other `mvn` command in
this repo - see the `skg-if-build-toolchain` skill for one-time setup. Don't run a full
`compile`/`test` just to format code; both goals below work standalone and are much
faster.

## Checking for violations (read-only)

Use this to see whether anything needs formatting - e.g. before a commit, or to
confirm a build failure is a formatting issue - without changing any files:

```powershell
$dest = "<repo-root>\.tools"
$env:JAVA_HOME = "$dest\jdk-21"
$env:PATH = "$dest\jdk-21\bin;$dest\apache-maven-3.9.16\bin;$env:PATH"
mvn -q -B spotless:check
```

Exit code `0` means everything is already formatted. A non-zero exit prints a unified
diff per violating file (truncated after the first few) plus a count of how many other
files also have violations - that's enough to confirm there's a problem and roughly how
widespread it is; don't dig further into the diff output itself, just move on to
`spotless:apply` to fix it.

## Applying fixes

Rewrites every violating file in place - reformats, and removes unused imports:

```powershell
$dest = "<repo-root>\.tools"
$env:JAVA_HOME = "$dest\jdk-21"
$env:PATH = "$dest\jdk-21\bin;$dest\apache-maven-3.9.16\bin;$env:PATH"
mvn -q -B spotless:apply
```

This can touch a large number of files across the whole tree in one run (it's not
scoped to files you've personally edited), so treat it like any other action that
rewrites working-tree files: check `git status`/`git diff` afterward and let the user
review before committing, same as after any other tool-driven bulk edit.

## Scoping to specific files

To format/check only certain files instead of the whole tree - e.g. after editing just
one or two classes - pass `-DspotlessFiles` with a comma-separated list of regexes
matched against each file's path:

```powershell
mvn -q -B spotless:apply "-DspotlessFiles=.*CrossrefProject.*\.java"
```

This still evaluates against the full `<includes>` list in `pom.xml`, it just skips
writing/reporting on files whose path doesn't match one of the regexes - so a run with
no output (exit `0`) can mean either "nothing matched" or "matched files were already
formatted"; confirm with `git status`/`git diff --stat` if that distinction matters.
For anything wider than a couple of specific files, it's simpler to just run the
unscoped command above and review the resulting diff.
