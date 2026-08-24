#!/usr/bin/env bash
# Puts a suitable JDK on PATH for the current Bash session. Idempotent, cross-platform.
#
# SOURCE THIS, do not execute it:
#
#     source .claude/skills/skg-if-build-toolchain/activate.sh
#
# A script run as a child process cannot mutate its caller's environment, so
# `./activate.sh` appears to succeed while leaving JAVA_HOME and PATH untouched.
#
# The Java feature version is read from pom.xml's <maven.compiler.release> - the single
# source of truth for this repo. Provisioning (when .tools/jdk-<major> is missing) is
# delegated to bootstrap-jdk.ps1 on Windows and bootstrap-jdk.sh elsewhere rather than
# reimplemented here, so each download recipe exists in exactly one file.
#
# PREFERENCE ORDER - see the "Linux/macOS" section of SKILL.md:
#
#     .tools/jdk-<major> present?            -> use it
#     else ambient java major >= required?   -> use it, export nothing   (devcontainer / CI)
#     else                                   -> provision, then use it
#
# The ambient check sits ahead of provisioning deliberately: the devcontainer image
# already ships a correct JDK, and downloading a second 200MB copy over it would be pure
# waste. It also has to be a real *version* comparison - an earlier revision fell back to
# whatever `java` was on PATH without checking, so a JDK 17 box got a reassuring success
# here and then a baffling "invalid target release: 21" from Maven much later.
#
# Maven is intentionally not handled here. Build through the ./mvnw wrapper, which pins
# its own version in .mvn/wrapper/maven-wrapper.properties.
#
# MSYS PATH NOTE (Windows/git-bash only): `pwd` in git-bash yields /c/... form, and that
# matters. A Windows drive-letter path (C:/repo/.tools/...) breaks silently once placed in
# PATH, because `:` is Bash's PATH separator: C:/repo/... is split right after the C into
# two useless entries (`C`, and `/repo/...` missing its drive root). The symptom is a bare
# `command not found` (exit 127) that gives no hint the problem is the path format rather
# than a missing or broken toolchain. Everything below stays in /c/... form for that
# reason - never interpolate a raw Windows path into PATH or JAVA_HOME here.

# True when $1 looks like a JDK home. Both names are checked because the same repo is used
# from git-bash (where the portable JDK's launcher is java.exe) and from Linux.
_skg_jdk_present() {
    [ -x "$1/bin/java" ] || [ -x "$1/bin/java.exe" ]
}

# Prints the feature version of the `java` on PATH (8 for a 1.8 JVM), or fails if there is
# no java, or its version can't be parsed. java.specification.version is used rather than
# java.version because it is already normalised to the feature version.
_skg_java_major() {
    local raw
    command -v java >/dev/null 2>&1 || return 1
    raw="$(java -XshowSettings:properties -version 2>&1 |
        sed -nE 's/^[[:space:]]*java\.specification\.version[[:space:]]*=[[:space:]]*([0-9.]+).*/\1/p' | head -1)"
    raw="${raw#1.}"     # 1.8 -> 8
    raw="${raw%%.*}"    # 11.0.2 -> 11 (only very old JDKs report a dotted value here)
    # Screen non-numeric junk before it reaches `-ge`, which would otherwise abort the
    # shell with a syntax error rather than falling through to provisioning.
    case "$raw" in
        '' | *[!0-9]*) return 1 ;;
    esac
    printf '%s\n' "$raw"
}

_skg_activate() {
    local script_dir repo_root pom jdk_major jdk_home ambient

    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    repo_root="$(cd "$script_dir/../../.." && pwd)"
    pom="$repo_root/pom.xml"

    jdk_major="$(sed -nE 's/.*<maven\.compiler\.release>[[:space:]]*([0-9]+).*/\1/p' "$pom" | head -1)"
    if [ -z "$jdk_major" ]; then
        echo "activate.sh: could not read <maven.compiler.release> from $pom - it is the single source of truth for this repo's Java version." >&2
        return 1
    fi

    jdk_home="$repo_root/.tools/jdk-$jdk_major"

    if _skg_jdk_present "$jdk_home"; then
        : # Already provisioned - fall through to the exports below.
    elif ambient="$(_skg_java_major)" && [ "$ambient" -ge "$jdk_major" ]; then
        # Devcontainer / Linux CI: the image's own JDK is good enough. Export nothing -
        # JAVA_HOME and PATH are already whatever that image set them to. `-ge` and not
        # `-eq`: maven.compiler.release=$jdk_major builds fine on a newer JDK, so pinning
        # equality here would reject valid setups.
        return 0
    else
        # Provisioning dispatch. Each script owns its own platform support, including the
        # refusals - bootstrap-jdk.sh is what declines macOS and git-bash, not this file.
        if command -v powershell.exe >/dev/null 2>&1; then
            powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$script_dir/bootstrap-jdk.ps1" || return 1
        else
            # `bash <path>`, not `./<path>`: a file authored on the Windows dev box has no
            # executable bit, and git won't invent one on checkout.
            bash "$script_dir/bootstrap-jdk.sh" || return 1
        fi
        if ! _skg_jdk_present "$jdk_home"; then
            echo "activate.sh: provisioning reported success but there is still no java under $jdk_home/bin. Install a JDK $jdk_major or newer (the version pom.xml's <maven.compiler.release> declares) and put it on PATH, or use the devcontainer, which ships one." >&2
            return 1
        fi
    fi

    export JAVA_HOME="$jdk_home"
    # Guard against a repeated source stacking duplicate entries onto PATH.
    case ":$PATH:" in
        *":$jdk_home/bin:"*) ;;
        *) export PATH="$jdk_home/bin:$PATH" ;;
    esac
}

_skg_activate
_skg_rc=$?
unset -f _skg_activate _skg_jdk_present _skg_java_major
# Make the sourced file exit with the function status, not the status of unset.
( exit $_skg_rc )
