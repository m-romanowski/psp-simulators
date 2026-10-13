#!/usr/bin/env bash
set -euo pipefail

# Runs the one command a newcomer with nothing but Docker runs, and asserts the
# promise around it: the image builds from this repository, the stack reaches
# healthy, and the addresses the README hands that newcomer answer over the
# published port. `check` builds no image and the browser suite drives an
# already-running simulator, so this script is the only thing in this
# repository that walks this path. The build is forced rather than left to
# `up`, which reuses an image already on the machine and would let a Dockerfile
# that no longer builds pass here on a stale one.
#
# The stack then comes up a second time, under a second project name, while the
# first one's containers still exist stopped. That is the machine state a
# tester who has run this stack before is on, and the only state in which a
# resource the compose file pins to a fixed, daemon-global name - a container
# name, a network name, a volume name - refuses to start. A clean runner alone
# never meets it. Stopping the first stack rather than removing it is what
# frees the published ports while keeping the names taken.
#
# It leaves nothing behind, so it is runnable on a working machine and not only
# on a runner that is thrown away afterwards.

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

prefix="${ONE_COMMAND_PROJECT_PREFIX:-one-command}"
first="${prefix}-first"
second="${prefix}-second"
base_url="${ONE_COMMAND_BASE_URL:-http://127.0.0.1:8080}"
wait_timeout="${ONE_COMMAND_WAIT_TIMEOUT:-300}"
summary="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
work="$(mktemp -d)"
body="$work/body"

cleanup() {
  for project in "$second" "$first"; do
    docker compose -p "$project" down --volumes --rmi local --remove-orphans >/dev/null 2>&1 || true
  done
  rm -rf "$work"
}
trap cleanup EXIT

explain_the_stack() {
  echo "----- docker compose -p $1 ps --all -----"
  docker compose -p "$1" ps --all || true
  echo "----- docker compose -p $1 logs, last 80 lines -----"
  docker compose -p "$1" logs --tail 80 || true
}

bring_up() {
  local project="$1" began took
  began="$(date +%s)"
  if ! docker compose -p "$project" up --build --wait --wait-timeout "$wait_timeout"; then
    echo "::error::\`docker compose up --wait\` did not bring up a healthy stack as project $project, after $(($(date +%s) - began))s"
    explain_the_stack "$project"
    return 1
  fi
  took="$(($(date +%s) - began))"
  echo "project $project built and healthy in ${took}s"
  echo "$took" >"$work/$project.took"
}

answers_with() {
  local label="$1" want="$2" path="$3" code
  code="$(curl -sS --max-time 20 -o "$body" -w '%{http_code}' "$base_url$path")" || true
  if [ "$code" != "$want" ]; then
    echo "::error::$label: $base_url$path answered $code, not $want"
    head -c 400 "$body" || true
    echo
    return 1
  fi
  echo "$label: $base_url$path answered $code"
}

serves_a_page() {
  answers_with "$1" 200 "$2" || return 1
  if ! grep -qi '<html' "$body"; then
    echo "::error::$1: $base_url$2 answered 200 with no page in it"
    head -c 400 "$body" || true
    echo
    return 1
  fi
}

readiness_reports_the_database_up() {
  answers_with "readiness" 200 "/actuator/health/readiness" || return 1
  if ! python3 - "$body" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    health = json.load(handle)

whole = health.get("status")
database = health.get("components", {}).get("db", {}).get("status")
print(f"readiness: status={whole} components.db.status={database}")
if whole != "UP" or database != "UP":
    print("::error::readiness did not report itself and its database UP")
    sys.exit(1)
PY
  then
    head -c 400 "$body" || true
    echo
    return 1
  fi
}

echo "===== the one command, as a fresh clone runs it ====="
bring_up "$first"

echo "===== what that command promises, over the published port ====="
promises=0
serves_a_page "the tester UI" "/ui" || promises=1
readiness_reports_the_database_up || promises=1
serves_a_page "the generated routes" "/openapi/index.html" || promises=1
if [ "$promises" -ne 0 ]; then
  explain_the_stack "$first"
  echo "::error::the stack was healthy but did not answer what \`docker compose up --wait\` promises"
  exit 1
fi

echo "===== the same stack again, on a machine that already holds one ====="
docker compose -p "$first" stop
docker compose -p "$first" ps --all
bring_up "$second"
if ! readiness_reports_the_database_up; then
  explain_the_stack "$second"
  echo "::error::the second stack was healthy but did not answer over the port the first one had"
  exit 1
fi

{
  echo "### The one command works"
  echo
  echo "| What | Result |"
  echo "|------|--------|"
  echo "| the image built and the stack reached healthy | $(cat "$work/$first.took")s |"
  echo "| \`/ui\`, readiness with its database, \`/openapi/index.html\` | answered over $base_url |"
  echo "| a second stack beside one this machine already held | healthy in $(cat "$work/$second.took")s |"
} >>"$summary"

echo "the one command builds, starts, answers, and coexists with a stack this machine already holds"
