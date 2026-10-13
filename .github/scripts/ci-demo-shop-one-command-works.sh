#!/usr/bin/env bash
set -euo pipefail

# Runs the one command `demo-shop/README.md` hands a reader, and asserts the
# promise around it: the simulator image builds from this repository, the shop
# image builds from `demo-shop/Dockerfile`, the stack reaches healthy, and both
# addresses the README names answer over their published ports. Nothing else in
# this repository builds the shop's image or reads the compose file that wires
# the two together, so without this the README's headline command is the only
# statement that it works.
#
# It does not repeat the root guard's second-stack probe. `demo-shop/compose.yaml`
# pins a project name, which is the namespace compose reconciles against rather
# than a daemon-global resource name: a second `up` of the same project adopts
# the containers already there instead of refusing, and `-p` overrides the pin.
# A fixed `container_name`, which this file carries nowhere, is the shape that
# refuses, and the root guard covers it on the image both stacks share.
#
# It does assert the one thing no single-stack run can: the root stack is up,
# answering on its own published port, for the whole of the demo shop's run.
# Somebody testing their integration against the simulator and then opening the
# demo shop has both stacks on the machine at once, and the only state that
# catches a host port one of them took from the other is both of them running.
# Each guard on its own starts on a clean runner and would pass either way.
#
# It leaves nothing behind, so it is runnable on a working machine and not only
# on a runner that is thrown away afterwards.

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
shop="$root/demo-shop"

project="${DEMO_SHOP_PROJECT:-demo-shop-one-command}"
root_project="${DEMO_SHOP_ROOT_PROJECT:-demo-shop-beside-the-root-stack}"
shop_url="${DEMO_SHOP_URL:-http://127.0.0.1:8081}"
simulator_url="${DEMO_SHOP_SIMULATOR_URL:-http://127.0.0.1:8082}"
root_simulator_url="${DEMO_SHOP_ROOT_SIMULATOR_URL:-http://127.0.0.1:8080}"
wait_timeout="${DEMO_SHOP_WAIT_TIMEOUT:-300}"
summary="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
work="$(mktemp -d)"
body="$work/body"

cleanup() {
  docker compose -p "$project" --project-directory "$shop" down \
    --volumes --rmi local --remove-orphans >/dev/null 2>&1 || true
  docker compose -p "$root_project" --project-directory "$root" down \
    --volumes --rmi local --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

compose() {
  docker compose -p "$project" --project-directory "$shop" "$@"
}

root_compose() {
  docker compose -p "$root_project" --project-directory "$root" "$@"
}

explain_the_stack() {
  echo "----- docker compose -p $project ps --all -----"
  compose ps --all || true
  echo "----- docker compose -p $project logs, last 80 lines -----"
  compose logs --tail 80 || true
  echo "----- docker compose -p $root_project ps --all -----"
  root_compose ps --all || true
}

serves_a_page() {
  local label="$1" url="$2" code
  code="$(curl -sS --max-time 20 -o "$body" -w '%{http_code}' "$url")" || true
  if [ "$code" != "200" ]; then
    echo "::error::$label: $url answered $code, not 200"
    head -c 400 "$body" || true
    echo
    return 1
  fi
  if ! grep -qi '<html' "$body"; then
    echo "::error::$label: $url answered 200 with no page in it"
    head -c 400 "$body" || true
    echo
    return 1
  fi
  echo "$label: $url answered 200 with a page"
}

echo "===== the simulator's own stack, already running on this machine ====="
root_began="$(date +%s)"
if ! root_compose up --build --wait --wait-timeout "$wait_timeout"; then
  echo "::error::the root stack did not reach healthy, after $(($(date +%s) - root_began))s"
  echo "----- docker compose -p $root_project ps --all -----"
  root_compose ps --all || true
  echo "----- docker compose -p $root_project logs, last 80 lines -----"
  root_compose logs --tail 80 || true
  exit 1
fi
root_took="$(($(date +%s) - root_began))"
echo "the simulator's own stack built and healthy in ${root_took}s"
if ! serves_a_page "the simulator the root README promises" "$root_simulator_url/ui"; then
  echo "----- docker compose -p $root_project logs, last 80 lines -----"
  root_compose logs --tail 80 || true
  echo "::error::the root stack was healthy but did not answer over its own published port"
  exit 1
fi

echo "===== the one command, as a reader of demo-shop/README.md runs it ====="
began="$(date +%s)"
if ! compose up --build --wait --wait-timeout "$wait_timeout"; then
  echo "::error::\`docker compose up --build\` did not bring up a healthy stack, after $(($(date +%s) - began))s"
  explain_the_stack
  exit 1
fi
took="$(($(date +%s) - began))"
echo "the shop, the simulator and their database built and healthy in ${took}s"

echo "===== what that command promises, over the published ports ====="
promises=0
serves_a_page "the shop" "$shop_url" || promises=1
serves_a_page "the simulator's UI" "$simulator_url/ui" || promises=1
serves_a_page "the root stack's simulator, still up" "$root_simulator_url/ui" || promises=1
if [ "$promises" -ne 0 ]; then
  explain_the_stack
  echo "::error::the stack was healthy but did not answer what demo-shop/README.md promises"
  exit 1
fi

{
  echo "### The demo shop's one command works"
  echo
  echo "| What | Result |"
  echo "|------|--------|"
  echo "| both images built and the stack reached healthy | ${took}s |"
  echo "| the shop | answered over $shop_url |"
  echo "| the simulator's UI | answered over $simulator_url/ui |"
  echo "| the root stack beside it, throughout | answered over $root_simulator_url/ui |"
} >>"$summary"

echo "the demo shop's one command builds both images, starts the stack, answers what its README promises, and does so beside the simulator's own stack"
