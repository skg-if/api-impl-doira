---
name: code-review-graph-setup
description: One-time local setup for the code-review-graph MCP server (knowledge graph of this codebase) on a fresh checkout. Use whenever the code-review-graph MCP tools (query_graph_tool, get_impact_radius_tool, detect_changes_tool, semantic_search_nodes_tool, etc. — see the graph-tools section in CLAUDE.md) aren't showing up as available tools, or `.tools\venv-crg\` doesn't exist yet.
---

# code-review-graph: fresh-checkout setup

[.mcp.json](../../../.mcp.json) already registers the `code-review-graph` MCP server for
this repo, and [CLAUDE.md](../../../CLAUDE.md) already tells agents to prefer its tools
over Grep/Glob/Read. Neither of those alone is enough on a **fresh clone**: the actual
Python venv the server runs from lives in `.tools/venv-crg/`, which is gitignored (same
reasoning as [portable-python](../portable-python/SKILL.md) and
[jq-json](../jq-json/SKILL.md) — no tool installed system-wide on this machine) and so
never ships with the repo. Until it's rebuilt, the MCP server in `.mcp.json` fails to
launch and none of the graph tools are available — Claude silently falls back to
Grep/Glob/Read, no error is shown.

**Symptom this fixes**: asking for `query_graph_tool`/`get_impact_radius_tool`/etc. and
getting "no such tool", or noticing you're still grepping for callers/tests instead of
querying the graph.

## One-time setup (per checkout)

Skip straight to "Build the graph" if `.tools\venv-crg\Scripts\code-review-graph.exe`
already exists.

```powershell
# 1. Needs the portable Python interpreter first - see portable-python/SKILL.md
#    if .tools\python\python.exe doesn't exist yet.
.tools\python\python.exe -m venv .tools\venv-crg
.tools\venv-crg\Scripts\python.exe -m pip install --quiet code-review-graph
.tools\venv-crg\Scripts\code-review-graph.exe --version   # confirm it runs
```

`.mcp.json` already points at `${CLAUDE_PROJECT_DIR}\.tools\venv-crg\Scripts\python.exe`
— that variable resolves to this checkout's own absolute path, so no edit to `.mcp.json`
is needed regardless of where the repo was cloned to.

## Build the graph

```powershell
.tools\venv-crg\Scripts\code-review-graph.exe build
.tools\venv-crg\Scripts\code-review-graph.exe status   # sanity-check node/edge counts > 0
```

This creates `.code-review-graph/` (a SQLite DB) at the repo root — already covered by
a `.gitignore` entry, nothing to add. Gitignored files (`target/`, `.tools/`, etc.) are
skipped automatically since this is a git repo; no `.code-review-graphignore` needed.

## After building

**Restart Claude Code / reconnect the MCP session** — it only picks up `.mcp.json` at
session start, so the graph tools won't appear until you do.

Two hooks already installed in [.claude/settings.json](../../settings.json) keep the
graph current automatically from then on: `PostToolUse` runs a background
`code-review-graph update --skip-flows` after every Edit/Write, and `SessionStart` runs
`code-review-graph status`. Both check `command -v code-review-graph` first and no-op
silently if the venv isn't set up yet — so it's safe to have them installed before
running this setup.

## Manual commands (rarely needed — the hooks above cover normal use)

```powershell
.tools\venv-crg\Scripts\code-review-graph.exe update     # incremental re-parse of changed files
.tools\venv-crg\Scripts\code-review-graph.exe watch       # keep it updated continuously during a long session
.tools\venv-crg\Scripts\code-review-graph.exe detect-changes --brief   # read-only diff-impact check
```
