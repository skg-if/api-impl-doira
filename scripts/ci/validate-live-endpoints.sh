#!/usr/bin/env bash
# Validates the live /datacite and /crossref products/grants endpoints against
# src/main/openapi/skg-if-openapi.yaml, using a Stoplight Prism proxy in front of the real,
# running app - the same "Prism as validator" pattern skg-if/api's
# .github/WORFLOW.md uses for its own live-implementation workflow, adapted for GitHub Actions CI.
#
# The endpoints under test are live pass-throughs to the public, unauthenticated
# api.datacite.org / api.crossref.org APIs (no local DB - see application.properties), so this
# script makes real outbound network calls. A DataCite/Crossref outage can therefore fail this
# job without there being an actual contract bug in this repo.
set -uo pipefail

APP_PORT=8080
APP_BASE="http://127.0.0.1:${APP_PORT}/skg-if/api"
PRISM_PORT=4010
PRISM_BASE="http://127.0.0.1:${PRISM_PORT}"
SPEC_PATH="src/main/openapi/skg-if-openapi.yaml"
NOT_FOUND_STATUSES="404 501"

PROVIDERS="datacite crossref"
RESOURCES="products grants"

APP_PID=""
PRISM_PID=""
FAILED=0
declare -a SUMMARY

cleanup() {
    [ -n "$PRISM_PID" ] && kill "$PRISM_PID" 2>/dev/null
    [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null
}
trap cleanup EXIT

wait_for_port() {
    # Polls with curl rather than bash's /dev/tcp (not reliably available on every bash build)
    # or the app's own resource paths (which would trigger a real DataCite/Crossref call on
    # every retry) - any HTTP response, even a 404 from an unmapped path, proves the listener
    # is up without hitting a real endpoint.
    local port=$1
    local timeout=$2
    local waited=0
    until curl -s -o /dev/null "http://127.0.0.1:${port}/"; do
        waited=$((waited + 1))
        if [ "$waited" -ge "$timeout" ]; then
            echo "Timed out waiting for port ${port} after ${timeout}s" >&2
            return 1
        fi
        sleep 1
    done
    return 0
}

# A 404/501 can come from two very different places: the real app legitimately saying "not
# found"/"not implemented" (its errors use type "https://skg-if.github.io/api/errors#..." - see
# JsonLdResponses.notFound), or Prism itself failing to route the request at all (e.g.
# NO_PATH_MATCHED_ERROR - its own errors all use type "https://stoplight.io/prism/errors#...").
# The latter means the check never even reached the app, so it must fail, not skip.
is_prism_own_error() {
    local body_file=$1
    jq -e '(.type // "") | startswith("https://stoplight.io/prism/errors#")' "$body_file" >/dev/null 2>&1
}

classify_status() {
    local status=$1
    local body_file=$2
    if [[ " $NOT_FOUND_STATUSES " == *" $status "* ]]; then
        if is_prism_own_error "$body_file"; then
            echo "fail"
        else
            echo "skip"
        fi
    elif [ "$status" -ge 200 ] && [ "$status" -lt 300 ]; then
        echo "pass"
    else
        echo "fail"
    fi
}

echo "Installing jq (prism itself is run via npx, not installed globally - see below)..."
apt-get update -qq
apt-get install -y --no-install-recommends nodejs npm jq >/dev/null

# Deliberately npx, not `npm install -g @stoplight/prism-cli`: a global install resolves nested
# deps (e.g. @faker-js/faker, pulled in by prism-http's mocker code even though only proxy mode
# is used here) against apt's often-old bundled npm, which can fail with a bare
# "Cannot find module '@faker-js/faker'" at prism startup. `npx` resolves/caches the package
# fresh per npm's own installer instead, which doesn't hit this. Version pinned to major 4 to
# match the reference workflow's `stoplight/prism:4` Docker tag.
run_prism() {
    npx --yes @stoplight/prism-cli@4 "$@"
}

# Pre-warm npx's package cache once, outside the loop below, so: (a) a real resolution/module
# failure surfaces immediately with a clear message instead of buried in the first iteration's
# 30s startup-timeout log, and (b) every iteration inside the loop hits a warm cache instead of
# re-resolving from the registry.
echo "Pre-warming prism-cli via npx..."
if ! run_prism --version; then
    echo "Failed to resolve/run @stoplight/prism-cli via npx - see output above" >&2
    exit 1
fi

echo "Starting the app (target/quarkus-app/quarkus-run.jar)..."
java -jar target/quarkus-app/quarkus-run.jar &
APP_PID=$!
if ! wait_for_port "$APP_PORT" 60; then
    echo "App failed to start on port ${APP_PORT}" >&2
    exit 1
fi
echo "App is up (pid ${APP_PID})."

echo "Validating the live implementation against the SKG-IF OpenAPI spec (${SPEC_PATH}) via a Stoplight Prism proxy..."

for PROVIDER in $PROVIDERS; do
    for RESOURCE in $RESOURCES; do
        echo "=== ${PROVIDER} ${RESOURCE} ==="

        run_prism proxy -h 127.0.0.1 -p "$PRISM_PORT" "$SPEC_PATH" \
            "${APP_BASE}/${PROVIDER}" --errors >prism.log 2>&1 &
        PRISM_PID=$!
        if ! wait_for_port "$PRISM_PORT" 30; then
            echo "Prism proxy failed to start for ${PROVIDER} ${RESOURCE}" >&2
            tail -n 40 prism.log >&2
            SUMMARY+=("${PROVIDER} ${RESOURCE} list=fail(prism-start) by-id=skip")
            FAILED=1
            kill "$PRISM_PID" 2>/dev/null
            PRISM_PID=""
            continue
        fi

        if [ "$RESOURCE" = "products" ]; then
            FILTER="$CONTRACT_TEST_FILTER_PRODUCTS"
        else
            FILTER="$CONTRACT_TEST_FILTER_GRANTS"
        fi

        ENCODED_FILTER=$(jq -rn --arg v "$FILTER" '$v|@uri')
        LIST_URL="${PRISM_BASE}/${RESOURCE}?filter=${ENCODED_FILTER}"
        LIST_STATUS=$(curl -s -G --data-urlencode "filter=${FILTER}" \
            -o resp.json -w '%{http_code}' "${PRISM_BASE}/${RESOURCE}")
        LIST_RESULT=$(classify_status "$LIST_STATUS" resp.json)
        if [ "$LIST_RESULT" = "fail" ]; then
            echo "FAIL: GET ${LIST_URL} -> HTTP ${LIST_STATUS}" >&2
            cat resp.json >&2
            tail -n 40 prism.log >&2
            FAILED=1
        else
            echo "${LIST_RESULT}: GET ${LIST_URL} -> HTTP ${LIST_STATUS}"
        fi

        BYID_RESULT="skip"
        if [ "$LIST_RESULT" = "pass" ]; then
            ID=$(jq -r '."@graph"[0].local_identifier // empty' resp.json 2>/dev/null)
            if [ -n "$ID" ]; then
                # local_identifier is a full https://doi.org/... URL (see LocalIdentifiers.java),
                # so it always contains slashes - Prism's OpenAPI path matching treats
                # {local_identifier} as a single path segment (standard OpenAPI semantics, unlike
                # the app's own JAX-RS ".+" regex path), so an un-encoded slash-bearing id 404s at
                # Prism itself with NO_PATH_MATCHED_ERROR before ever reaching the app. Percent-
                # encoding it into one segment lets Prism route it; the app decodes it back fine.
                ENCODED_ID=$(jq -rn --arg v "$ID" '$v|@uri')
                BYID_URL="${PRISM_BASE}/${RESOURCE}/${ENCODED_ID}"
                BYID_STATUS=$(curl -s -o resp_byid.json -w '%{http_code}' "$BYID_URL")
                BYID_RESULT=$(classify_status "$BYID_STATUS" resp_byid.json)
                if [ "$BYID_RESULT" = "fail" ]; then
                    echo "FAIL: GET ${BYID_URL} (id=${ID}) -> HTTP ${BYID_STATUS}" >&2
                    cat resp_byid.json >&2
                    tail -n 40 prism.log >&2
                    FAILED=1
                else
                    echo "${BYID_RESULT}: GET ${BYID_URL} (id=${ID}) -> HTTP ${BYID_STATUS}"
                fi
            else
                echo "skip: no results for filter=${FILTER}, nothing to chain to"
            fi
        fi

        SUMMARY+=("${PROVIDER} ${RESOURCE} list=${LIST_RESULT} by-id=${BYID_RESULT}")

        kill "$PRISM_PID" 2>/dev/null
        wait "$PRISM_PID" 2>/dev/null
        PRISM_PID=""
    done
done

echo
echo "=== Summary ==="
printf '%s\n' "${SUMMARY[@]}"

exit "$FAILED"
