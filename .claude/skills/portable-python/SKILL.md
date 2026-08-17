---
name: portable-python
description: Set up and use a portable, self-contained Python 3.12 interpreter (with pip) for this repo — running one-off scripts, data wrangling, or anything else needing `python`/`pip` when no system Python is meant to be relied on. Use whenever a task needs to run a Python script or install a Python package in this repo.
---

# Portable Python toolchain

There is no system-wide Python meant to be relied on for this project, and one should
not be installed globally (`winget`/`choco`/the Windows Store) — same reasoning as the
JDK/Maven toolchain in [skg-if-build-toolchain](../skg-if-build-toolchain/SKILL.md) and the jq binary
in [jq-json](../jq-json/SKILL.md): keep tooling scoped to this project, not the user's
machine. Use a **portable, self-contained** interpreter cached in `.tools/python/` at
the repo root (gitignored) — never system-wide, never persisted to user/machine
environment variables.

Caching it in-repo (instead of the session scratchpad) means it survives across
conversations and only needs downloading once per checkout.

## One-time setup (per checkout)

Skip this entirely if `.tools\python\python.exe` already exists — go straight to
"Invoking python/pip" below.

```powershell
$dest = "<repo-root>\.tools\python"
New-Item -ItemType Directory -Force $dest | Out-Null

# python-build-standalone (the project behind uv's managed Pythons) - fully
# self-contained "install_only" build, no installer, relocatable. Bump the release
# tag/version in the URL if a newer one is needed later.
Invoke-WebRequest -Uri "https://github.com/astral-sh/python-build-standalone/releases/download/20260807/cpython-3.12.13+20260807-x86_64-pc-windows-msvc-install_only.tar.gz" -OutFile "$dest\..\python.tar.gz"
tar -xzf "$dest\..\python.tar.gz" -C $dest --strip-components=1
Remove-Item "$dest\..\python.tar.gz" -Force

& "$dest\python.exe" --version       # confirm it runs
& "$dest\python.exe" -m pip --version  # pip is bundled already
```

## Invoking `python`/`pip`

Call the binary by its full path — don't add it to `PATH` or rely on a bare `python`
on this machine:

```powershell
.tools\python\python.exe script.py
.tools\python\python.exe -m pip install --quiet requests
```

From Git Bash the same path works directly: `.tools/python/python.exe script.py`.

If a task needs isolated/reproducible dependencies (rather than installing straight
into the shared portable interpreter), create a venv from it and use *that* venv's
python/pip — still entirely inside `.tools/`, nothing global:

```powershell
.tools\python\python.exe -m venv .tools\venv
.tools\venv\Scripts\python.exe -m pip install --quiet -r requirements.txt
.tools\venv\Scripts\python.exe script.py
```

Set `PATH`/env vars this way in every command/tool invocation that needs them (each
Bash/PowerShell tool call starts a fresh process) - do not attempt to persist them to
the user or machine environment.

## Notes

- This build already bundles `pip` — no separate `get-pip.py` step needed (unlike the
  bare python.org "embeddable" package, which deliberately omits pip).
- Packages installed with `.tools\python\python.exe -m pip install ...` land inside
  `.tools\python\Lib\site-packages` — still fully contained in `.tools/`, still
  gitignored, still gone on a fresh checkout unless reinstalled.
- If a task needs a different Python minor version, download the matching asset from
  the same [python-build-standalone releases page](https://github.com/astral-sh/python-build-standalone/releases)
  (look for `<version>-x86_64-pc-windows-msvc-install_only.tar.gz`) into its own
  `.tools/python-<version>/` directory instead of overwriting this one.
