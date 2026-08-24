---
name: jq-json
description: Use jq to filter, transform, query, and reshape JSON from the command line — picking/mapping fields, filtering by value, flattening nested arrays, grouping/sorting, converting JSON to/from CSV, editing files in place, and streaming through large files without loading them all into memory. Reach for this whenever JSON needs to be sliced, queried, reshaped, or piped through a shell pipeline — API responses, golden JSON-LD fixtures, config files, curl output — even if the user just says "grab this field from the JSON," "turn this response into a table," or "how many items have status X." Prefer jq over writing a one-off Python/Node script to parse JSON when the transformation is expressible as a filter — it's faster to write, faster to run, and composes with the rest of the shell pipeline.
---

# jq for JSON on the command line

jq is a filter language for JSON: input JSON in, transformed JSON (or text) out. Most jobs are one filter expression, no throwaway script needed. Reach for a script (Python/Node) instead when the logic needs real branching, loops with side effects, or talks to something outside the JSON itself.

## No system-wide jq on this machine

There is no jq installed on this machine, and one should not be installed globally
(`winget`/`choco`) — same reasoning as the JDK/Maven toolchain in
[skg-if-build-toolchain](../skg-if-build-toolchain/SKILL.md): keep tooling scoped to this project,
not the user's machine. Use a **portable, self-contained** binary cached in `.tools/jq/`
at the repo root (gitignored) — never system-wide, never persisted to user/machine
environment variables.

Caching it in-repo (instead of the session scratchpad) means it survives across
conversations and only needs downloading once per checkout.

### One-time setup (per checkout)

Skip this entirely if `.tools\jq\jq.exe` already exists — go straight to "Invoking jq" below.

```powershell
# Resolve the repo root from git so this works from any subdirectory (git prints
# forward slashes on Windows, so normalise them for the backslash paths below).
$repoRoot = (git rev-parse --show-toplevel) -replace '/', '\'
$dest = "$repoRoot\.tools\jq"
New-Item -ItemType Directory -Force $dest | Out-Null
Invoke-WebRequest -Uri "https://github.com/jqlang/jq/releases/latest/download/jq-windows-amd64.exe" -OutFile "$dest\jq.exe"
& "$dest\jq.exe" --version   # confirm it runs
```

### Invoking jq

Call the binary by its full path — don't add it to `PATH` or rely on a bare `jq` on this machine:

```powershell
.tools\jq\jq.exe '.items[] | select(.active)' file.json
```

From Git Bash the same path works directly: `.tools/jq/jq.exe '...' file.json`.

## The core mental model

jq applies a filter to a JSON value and produces a stream of JSON values. Filters compose with `|` the way shell commands compose with pipes. `.` is the input itself; everything else builds on it.

```bash
jq '.field' file.json                 # get a field
jq '.a.b.c' file.json                 # dotted path into nested objects
jq '.items[0]' file.json              # index into an array
jq '.items[]' file.json               # explode an array into a stream of values (note: no index)
jq '.items | length' file.json        # pipe into a function
```

(Examples below say `jq` for brevity — substitute `.tools\jq\jq.exe` / `.tools/jq/jq.exe`.)

## Common patterns

**Pick / reshape fields** — build a new object with `{}`:
```bash
jq '.items[] | {id, name, total: .amount}' file.json
# {id, name} is shorthand for {id: .id, name: .name}; rename with `key: .path`
```

**Filter by value** — `select()` keeps values where the condition is true:
```bash
jq '.items[] | select(.status == "active")' file.json
jq '.items[] | select(.amount > 100 and .region == "EU")' file.json
jq '[.items[] | select(.status == "active")]' file.json   # wrap in [] to get an array back, not a stream
```

**map** — transform every element of an array (map is shorthand for `[.[] | ...]`):
```bash
jq '[.items[] | .name]' file.json      # equivalent to:
jq 'map(.name)' file.json              # when the input is already the array
jq '.items | map(select(.active))' file.json
```

**Flatten nested arrays**:
```bash
jq '[.groups[].items[]]' file.json     # flatten one level via double iteration
jq 'flatten' file.json                 # flatten all levels of an already-nested array
jq 'flatten(1)' file.json              # flatten exactly one level
```

**group_by / sort_by** — both take a filter, not a bare field name, and `group_by` requires sorted input by that key (it sorts for you):
```bash
jq 'group_by(.category)' file.json                     # array of arrays, grouped
jq 'group_by(.category) | map({category: .[0].category, count: length})' file.json
jq 'sort_by(.date)' file.json
jq 'sort_by(.date) | reverse' file.json                 # newest first
```

**Aggregate**:
```bash
jq '[.items[].amount] | add' file.json          # sum
jq '.items | length' file.json                  # count
jq '[.items[].amount] | max' file.json
jq 'group_by(.status) | map({status: .[0].status, count: length})' file.json
```

**Combine multiple filters into one object** in a single pass (avoids re-reading the input):
```bash
jq '{total: ([.items[].amount] | add), count: (.items | length)}' file.json
```

## JSON ↔ CSV

**JSON array of objects → CSV**, header row first:
```bash
jq -r '(map(keys) | add | unique) as $cols | $cols, (.[] | [.[$cols[]]]) | @csv' file.json
```
For a known/fixed set of columns this is simpler and safer (no surprises if a row is missing a key):
```bash
jq -r '["id","name","amount"], (.[] | [.id, .name, .amount]) | @csv' file.json
```
`@tsv` works the same way for tab-separated output.

**CSV → JSON**: jq's own CSV parsing is line-oriented and doesn't handle quoted fields with embedded commas correctly. For real CSV, do the CSV parsing with a CSV-aware tool (Python's `csv` module, Miller `mlr`) and hand the result to jq only for further JSON reshaping.

## In-place editing

jq refuses to write to the file it's reading from (it would truncate the file before finishing reading it). Always write to a temp file and move it into place:
```bash
jq '.version = "2.0"' config.json > tmp.$$.json && mv tmp.$$.json config.json
```
There's no jq equivalent of `sed -i` — this two-step dance is the standard idiom.

## Large files and streaming

Default jq loads the whole input into memory as one JSON value — fine up to a few hundred MB, painful beyond that, especially for one giant array where you only need a filtered slice.

- **`--stream`** turns the input into a stream of `[path, value]` leaf events instead of building the full parsed structure, so you can filter before the whole thing is materialized. It's harder to write filters for — save it for files that genuinely don't fit in memory.
- **If the file is really "one JSON object per line"** (JSON Lines / NDJSON), you don't need `--stream` at all — just process it line-by-line, which is far simpler:
  ```bash
  jq -c 'select(.status == "error")' events.ndjson       # -c keeps output one-object-per-line too
  ```
- **`jq -s` ("slurp")** reads multiple JSON documents (e.g. one JSON value per line, or several files concatenated) into a single JSON array — the opposite problem from streaming, useful when you need to combine inputs rather than shrink them.

## Flags worth knowing

- **`-r` (raw output)** — strips the surrounding quotes from string results, so `jq '.name'` prints `"Alice"` but `jq -r '.name'` prints `Alice`. Always use `-r` when piping the result into another shell command that expects plain text (`xargs`, `grep`, variable assignment).
- **`-c` (compact)** — one JSON value per line instead of pretty-printed. Use for NDJSON output, or when piping to `wc -l` / `grep` to count or search matching records.
- **`-s` (slurp)** — read all inputs into one array first, then run the filter once, rather than running the filter once per input. Needed when the filter needs to see everything at once (e.g. `add`, `group_by`, `sort_by` across multiple files or multiple JSON docs in one file).
- **`-n` (null input)** — don't read any input JSON at all; useful for constructing JSON from scratch (`jq -n '{generated: true}'`) or when the real input comes in via `--argfile`/`--slurpfile`.
- **`--arg name value`** — inject a shell variable as a jq string (`$name`); **`--argjson name value`** — same but parsed as JSON (number/bool/object), not a string. Never string-interpolate shell variables directly into a jq filter — use `--arg`/`--argjson` so quoting and escaping are handled correctly.

## Quick troubleshooting

- `jq: error: null (null) has no keys` — a `select()` or field access hit a value that's `null` or a different type than expected somewhere in the stream; add `select(. != null)` upstream or use `.field?` (the `?` suffix suppresses errors from that access) instead of `.field`.
- Output has literal `"quotes"` around strings you wanted plain — you forgot `-r`.
- `group_by` gave one group per element instead of grouping properly — the grouping key isn't a valid short filter, or the array wasn't actually flat (double-check with `.[0]` what the elements look like).
- A filter that should print one thing per line instead runs once for the whole input — check whether you meant `.items[]` (stream) vs `.items` (single array value).
