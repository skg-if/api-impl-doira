#!/usr/bin/env bash
# Validates the live /datacite and /crossref products/grants endpoints against
# src/main/openapi/skg-if-openapi.yaml, using a Stoplight Prism proxy in front of the real,
# running app - the same "Prism as validator" pattern skg-if/api's
# .github/WORFLOW.md uses for its own live-implementation workflow, adapted for GitLab CI.
#
# The endpoints under test are live pass-throughs to the public, unauthenticated
# api.datacite.org / api.crossref.org APIs (no local DB - see application.properties), so this
# script makes real outbound network calls. A DataCite/Crossref outage can therefore fail this
# job without there being an actual contract bug in this repo.
#
# Known finding as of 2026-08-13, not yet fixed: the "grants" list checks (both providers) are
# EXPECTED to report fail today - Grant.titles/Grant.abstracts declare each
# language as a plain string in the spec, but the mapper emits an array per language (the same
# shape Product.titles/Product.abstracts actually declare and use). This is a real, pre-existing
# spec/implementation mismatch this script surfaces, not a false positive - left as a documented
# follow-up rather than fixed here.
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

classify_status() {
    local status=$1
    if [[ " $NOT_FOUND_STATUSES " == *" $status "* ]]; then
        echo "skip"
    elif [ "$status" -ge 200 ] && [ "$status" -lt 300 ]; then
        echo "pass"
    else
        echo "fail"
    fi
}

echo "Installing prism CLI and jq..."
apt-get update -qq
apt-get install -y --no-install-recommends nodejs npm jq >/dev/null
npm install -g @stoplight/prism-cli >/dev/null

echo "Starting the app (target/quarkus-app/quarkus-run.jar)..."
java -jar target/quarkus-app/quarkus-run.jar &
APP_PID=$!
if ! wait_for_port "$APP_PORT" 60; then
    echo "App failed to start on port ${APP_PORT}" >&2
    exit 1
fi
echo "App is up (pid ${APP_PID})."

for PROVIDER in $PROVIDERS; do
    for RESOURCE in $RESOURCES; do
        echo "=== ${PROVIDER} ${RESOURCE} ==="

        prism proxy -h 127.0.0.1 -p "$PRISM_PORT" "$SPEC_PATH" \
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

        LIST_STATUS=$(curl -s -G --data-urlencode "filter=${FILTER}" \
            -o resp.json -w '%{http_code}' "${PRISM_BASE}/${RESOURCE}")
        LIST_RESULT=$(classify_status "$LIST_STATUS")
        if [ "$LIST_RESULT" = "fail" ]; then
            echo "FAIL: GET ${RESOURCE} (filter=${FILTER}) -> HTTP ${LIST_STATUS}" >&2
            cat resp.json >&2
            tail -n 40 prism.log >&2
            FAILED=1
        else
            echo "${LIST_RESULT}: GET ${RESOURCE} (filter=${FILTER}) -> HTTP ${LIST_STATUS}"
        fi

        BYID_RESULT="skip"
        if [ "$LIST_RESULT" = "pass" ]; then
            ID=$(jq -r '."@graph"[0].local_identifier // empty' resp.json 2>/dev/null)
            if [ -n "$ID" ]; then
                BYID_STATUS=$(curl -s -o resp_byid.json -w '%{http_code}' "${PRISM_BASE}/${RESOURCE}/${ID}")
                BYID_RESULT=$(classify_status "$BYID_STATUS")
                if [ "$BYID_RESULT" = "fail" ]; then
                    echo "FAIL: GET ${RESOURCE}/${ID} -> HTTP ${BYID_STATUS}" >&2
                    cat resp_byid.json >&2
                    tail -n 40 prism.log >&2
                    FAILED=1
                else
                    echo "${BYID_RESULT}: GET ${RESOURCE}/${ID} -> HTTP ${BYID_STATUS}"
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
