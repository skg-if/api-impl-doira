---
name: markdown-lint
description: Lint a markdown file (e.g. SKG_IF_DOI_MAPPING.md, ONBOARDING.md) for structural errors — unclosed tags, broken tables, malformed links/headings — using the portable, pure-Python `pymarkdownlnt` linter instead of manually re-reading the file. Use whenever you've edited a markdown file and want to validate it, or whenever the user asks to "lint", "validate", or "check" a markdown file.
---

# Markdown linting

Validating a markdown edit by re-reading the file back into context costs tokens
proportional to the file's size. A linter's output is a handful of lines regardless
of file size — much cheaper for catching *structural* mistakes (an unclosed
`<details>` tag skewing every column to its right in a table row, a malformed table,
a broken reference-style link). It does **not** catch semantic issues (a stale claim,
a broken cross-reference to another section, wrong content) — those still need an
actual read.

Uses [`pymarkdownlnt`](https://pypi.org/project/pymarkdownlnt/), a pure-Python
markdown linter — no Node.js/`markdownlint-cli` needed, so it installs straight into
the portable interpreter from [portable-python](../portable-python/SKILL.md) with
nothing global.

## One-time setup (per checkout)

Requires the portable Python from [portable-python](../portable-python/SKILL.md) —
set that up first if `.tools\python\python.exe` doesn't exist yet.

Skip this if `.tools\python\python.exe -m pymarkdown version` already succeeds.

```powershell
.tools\python\python.exe -m pip install --quiet pymarkdownlnt
```

Packages land in `.tools\python\Lib\site-packages` — contained in the gitignored
`.tools/`, gone on a fresh checkout unless reinstalled (same as every other package
installed via the portable-python skill).

## Linting a file

```powershell
.tools\python\python.exe -m pymarkdown -c .claude\skills\markdown-lint\pymarkdown-config.json scan <file>.md
```

Exit code `0` and no output means clean. Otherwise it prints one line per finding:
`<file>:<line>:<col>: <rule-id>: <message> (<rule-name>)`.

From Git Bash the same paths work directly:
`.tools/python/python.exe -m pymarkdown -c .claude/skills/markdown-lint/pymarkdown-config.json scan <file>.md`

`scan` also accepts multiple paths/globs in one call - e.g. to check every split-out mapping doc
at once: `... scan SKG_IF_DOI_MAPPING*.md`.

## Why a custom config

[`pymarkdown-config.json`](pymarkdown-config.json) (tracked here, not under the
gitignored `.tools/`, so it survives across checkouts and is shared with anyone else
working in this repo) disables two rules that fire on every doc in this repo by
deliberate style, not by mistake:

- **MD013 (line-length)** — mapping tables like
  [`SKG_IF_DOI_MAPPING_PRODUCT.md`](../../../SKG_IF_DOI_MAPPING_PRODUCT.md) and
  [`SKG_IF_DOI_MAPPING_DATES.md`](../../../SKG_IF_DOI_MAPPING_DATES.md) intentionally pack one
  full field's worth of prose into a single table-row line (markdown tables can't
  span multiple lines per row), so rows regularly run past 80 or even 1000 characters.
- **MD033 (no-inline-html)** — those same tables rely on `<details>`/`<summary>`/`<em>`
  to make long per-field descriptions collapsible instead of bloating the table
  visually.

Without disabling these two, `scan` reports hundreds of lines of expected-style noise
that would drown out any genuine finding. If a *new* doc in this repo doesn't use
either pattern, run `pymarkdown scan` on it without `-c` (or with a file-specific
config) to get the full default ruleset instead.

## Notes

- `pymarkdown fix` can auto-fix some findings in place — review the diff before
  trusting it on a doc with hand-curated formatting like the mapping tables.
- This lints markdown *syntax/structure* only. For this repo's mapping docs
  specifically, `MappingDocConsistencyTest` (see `CLAUDE.md`) is the complementary
  check for *content* accuracy — it fails the build if a test fixture isn't
  mentioned in the doc, or if the doc links to a fixture that doesn't exist.
