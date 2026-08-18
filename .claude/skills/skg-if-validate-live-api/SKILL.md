---
name: skg-if-validate-live-api
description: Run scripts/ci/validate-live-endpoints.sh locally to reproduce the GitHub Actions "validate-live-api" job — validates the live DataCite/Crossref products/grants endpoints against the OpenAPI spec via a Stoplight Prism proxy in front of the built app. Use when a mapper/OpenAPI-spec change needs sanity-checking against real DataCite/Crossref responses, or to reproduce a CI failure from that job before pushing.
---

# Running the live-endpoint validation script locally

## Windows only

This skill only works on Windows. Every setup step below assumes a Windows checkout: the
portable Node.js download is the `win-x64` build, the skg-if-build-toolchain skill it depends on
provisions a Windows JDK/Maven layout, and the port check uses `netstat` (not `lsof`/`ss`). On
macOS/Linux, adapt the download URL, drop the Windows-specific `PATH`/`$env:` snippets, and use
the platform's own port-check command — or just run `scripts/ci/validate-live-endpoints.sh`
directly with `java`, `node`/`npx`, `jq`, and `curl` already on `PATH` from that platform's usual
package manager.

[`scripts/ci/validate-live-endpoints.sh`](../../../scripts/ci/validate-live-endpoints.sh) is the
same script `.github/workflows/maven-build.yml`'s `validate-live-api` job runs. It starts the
built app, puts
a Stoplight Prism proxy in front of it, and checks the live `/datacite` and `/crossref`
`products`/`grants` responses against `src/main/openapi/skg-if-openapi.yaml` — real outbound
calls to `api.datacite.org`/`api.crossref.org`, no local DB involved.

The script needs zero modification to run here: it's guarded by `set -uo pipefail` —
deliberately no `-e` — so its `apt-get install nodejs npm jq` line (there for the Linux CI
images) just prints a harmless `command not found` on this machine and the script carries on,
as long as `java`, `node`/`npx`, `jq`, and `curl` are already reachable on `PATH` for that one
invocation.

## No system-wide Node.js on this machine

There is no system-wide Node.js on this machine, and one should not be installed globally
(`winget`/`choco`) — same reasoning as the JDK/Maven toolchain in
[skg-if-build-toolchain](../skg-if-build-toolchain/SKILL.md) and the jq binary in
[jq-json](../jq-json/SKILL.md): keep tooling scoped to this project, not the user's machine. Use
a **portable, self-contained** Node.js build cached in `.tools/node/` at the repo root
(gitignored) — never system-wide, never persisted to user/machine environment variables.

### One-time setup (per checkout)

Skip this entirely if `.tools\node\node.exe` already exists — go straight to "Prerequisite:
build the app" below.

```bash
version=$(curl -s https://nodejs.org/dist/index.json | .tools/jq/jq.exe -r '[.[] | select(.lts != false)][0] | .version')
curl -sL -o .tools/node.zip "https://nodejs.org/dist/${version}/node-${version}-win-x64.zip"
cd .tools && unzip -q node.zip && mv "node-${version}-win-x64" node && rm node.zip && cd ..
.tools/node/node.exe --version   # confirm it runs
```

Any current Node LTS build works — only `npx`'s bundled `npm` is needed to fetch/run
`@stoplight/prism-cli@4`, the script doesn't otherwise depend on a specific Node version.

## Prerequisite: build the app

The script starts `target/quarkus-app/quarkus-run.jar` itself — it must already exist. Use
[skg-if-build-toolchain](../skg-if-build-toolchain/SKILL.md) to build it (tests aren't needed for this script,
just the artifact, so `-DskipTests` is fine here):

```bash
$dest = "<repo-root>\.tools"
$env:JAVA_HOME = "$dest\jdk-21"
$env:PATH = "$dest\jdk-21\bin;$dest\apache-maven-3.9.16\bin;$env:PATH"
mvn -q -B package -DskipTests
```

## Check ports 8080 and 4010 are free

The script hard-codes `APP_PORT=8080` and `PRISM_PORT=4010`. If a `mvn quarkus:dev` instance or
anything else already holds either port, `wait_for_port` inside the script will time out instead
of giving a clear "address in use" error:

```bash
netstat -ano | grep -E ":8080 |:4010 "   # expect no output; if not, stop whatever holds the port
```

## Running the script

From Git Bash, at the repo root, scope `PATH`/env vars to this one invocation — never persist
them to the user or machine environment:

```bash
export PATH="$(pwd)/.tools/jdk-21/bin:$(pwd)/.tools/node:$(pwd)/.tools/jq:$PATH"
export CONTRACT_TEST_FILTER_PRODUCTS="cf.search.title:tomography"
export CONTRACT_TEST_FILTER_GRANTS="cf.search.title:research"
bash scripts/ci/validate-live-endpoints.sh
```

The two `CONTRACT_TEST_FILTER_*` values above match what both CI jobs use by default — keep them
as-is to reproduce a CI run, or override them to search for something else.

The script writes `prism.log` (per-provider Prism proxy log, `*.log` is gitignored) and
`resp.json`/`resp_byid.json` (last HTTP response bodies, gitignored) into the repo root as
scratch output — safe to ignore or delete between runs.

## Reading the output

The script prints one `pass`/`fail`/`skip` line per `list`/`by-id` check per
provider/resource, then a final summary block, e.g.:

```text
=== Summary ===
datacite products list=pass by-id=pass
datacite grants list=pass by-id=pass
crossref products list=pass by-id=pass
crossref grants list=pass by-id=pass
```

- `fail(prism-start)` on the very first provider/resource checked (before any other output) is
  usually just the Prism proxy's cold npx start taking longer than the script's 30s timeout on
  this machine — re-run the script; subsequent providers/resources reuse a warm `npx` cache and
  don't see this.
- A real DataCite/Crossref outage can also fail this script independent of any bug here, since
  the checked endpoints are live pass-throughs to those public APIs.
