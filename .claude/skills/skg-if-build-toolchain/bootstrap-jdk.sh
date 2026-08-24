#!/usr/bin/env bash
# Provisions the portable Temurin JDK into .tools/ on Linux. Idempotent.
#
# Run it, don't source it - it mutates the filesystem, not the environment:
#
#     bash .claude/skills/skg-if-build-toolchain/bootstrap-jdk.sh
#
# Always invoked as `bash <path>` rather than `./<path>`, so no executable bit is required -
# a file created on the Windows dev box doesn't get one.
#
# WHY THIS EXISTS ALONGSIDE bootstrap-jdk.ps1: Adoptium serves .zip for Windows and .tar.gz
# for everything else, so Expand-Archive and tar cannot be unified. The duplication is the
# extractor only - both scripts read the version from pom.xml's <maven.compiler.release>, so
# no version pin is copied. ToolchainVersionConsistencyTest asserts the two agree on the
# Adoptium URL template and the .tools/jdk-<major> path convention.
#
# Platform detection lives here rather than in a shared platform.sh because this is its only
# caller today. If the portable-python / jq-json / validate-live-api skills are ever ported
# off their Windows-only downloads, the two `uname` cases below are the piece to extract.

# No `pipefail`: the `sed | head -1` below can leave sed killed by SIGPIPE once head closes,
# which pipefail would turn into a spurious failure.
set -eu

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../.." && pwd)"
pom="$repo_root/pom.xml"

# The same expression activate.sh uses - pom.xml owns this repo's Java version.
jdk_major="$(sed -nE 's/.*<maven\.compiler\.release>[[:space:]]*([0-9]+).*/\1/p' "$pom" | head -1)"
if [ -z "$jdk_major" ]; then
    echo "bootstrap-jdk.sh: could not read <maven.compiler.release> from $pom - it is the single source of truth for this repo's Java version." >&2
    exit 1
fi

jdk_home="$repo_root/.tools/jdk-$jdk_major"
if [ -x "$jdk_home/bin/java" ]; then
    echo "bootstrap-jdk.sh: JDK $jdk_major already present at $jdk_home"
    exit 0
fi

case "$(uname -s)" in
    Linux)
        os=linux
        ;;
    Darwin)
        # macOS Adoptium tarballs nest the JDK under Contents/Home, so the plain move below
        # would produce a JAVA_HOME that looks right and isn't. Refuse rather than guess.
        echo "bootstrap-jdk.sh: macOS provisioning is not implemented (the Adoptium tarball nests the JDK under Contents/Home). Install a JDK $jdk_major+ yourself, or use the devcontainer." >&2
        exit 1
        ;;
    *)
        echo "bootstrap-jdk.sh: unsupported platform '$(uname -s)'. On Windows, activate.ps1/activate.sh dispatch to bootstrap-jdk.ps1 instead." >&2
        exit 1
        ;;
esac

case "$(uname -m)" in
    x86_64 | amd64)
        arch=x64
        ;;
    aarch64 | arm64)
        arch=aarch64
        ;;
    *)
        echo "bootstrap-jdk.sh: unsupported architecture '$(uname -m)' - Adoptium publishes x64 and aarch64 builds." >&2
        exit 1
        ;;
esac

# Adoptium's "latest GA" redirect - no build number in the URL, so it can't go stale.
url="https://api.adoptium.net/v3/binary/latest/$jdk_major/ga/$os/$arch/jdk/hotspot/normal/eclipse"

tools="$repo_root/.tools"
tarball="$tools/temurin$jdk_major.tar.gz"
extract="$tools/jdk-$jdk_major-extracted"
mkdir -p "$tools"
rm -rf "$extract"
mkdir -p "$extract"

echo "Provisioning Temurin JDK $jdk_major ($os/$arch) into $jdk_home ..."

# curl's -f matters: without it a failing request writes the HTTP error page into the
# tarball, and tar then fails with "not in gzip format" - which reads like a corrupt
# download rather than a bad URL or an outage.
if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$tarball" "$url"
elif command -v wget >/dev/null 2>&1; then
    wget -qO "$tarball" "$url"
else
    echo "bootstrap-jdk.sh: neither curl nor wget is available to download the JDK." >&2
    exit 1
fi

tar -xzf "$tarball" -C "$extract"

# Adoptium tarballs contain a single top-level jdk-<major>.x.x+y directory; rename it to the
# stable, version-suffix-free path everything else refers to.
extracted="$(find "$extract" -mindepth 1 -maxdepth 1 -type d -name "jdk-$jdk_major*" | head -1)"
if [ -z "$extracted" ]; then
    echo "bootstrap-jdk.sh: downloaded archive did not contain an expected jdk-$jdk_major* directory under $extract" >&2
    exit 1
fi
mv "$extracted" "$jdk_home"
rm -rf "$tarball" "$extract"

echo "JDK $jdk_major ready at $jdk_home"
