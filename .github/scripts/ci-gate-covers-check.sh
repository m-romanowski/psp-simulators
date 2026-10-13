#!/usr/bin/env bash
set -euo pipefail

workflow="${1:?path to the gate workflow}"
gradlew="${2:-./gradlew}"
summary="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

ci_tasks="$(
  grep -E '^[[:space:]]*GATE_TASKS:[[:space:]]' "$workflow" |
    sed -E 's/^[[:space:]]*GATE_TASKS:[[:space:]]*//' |
    tr -s '[:space:]' ' '
)"

if [ -z "${ci_tasks// /}" ]; then
  echo "::error file=$workflow::no GATE_TASKS were found, so nothing proves CI runs the local gate"
  exit 1
fi

echo "GATE_TASKS declared across the workflow:$ci_tasks"

gradle_flags="${GRADLE_FLAGS:---console=plain}"

graph_of() {
  local graph
  # shellcheck disable=SC2086
  graph="$("$gradlew" $1 --dry-run $gradle_flags | sed -n -E 's/^(:[A-Za-z0-9_:.-]+) SKIPPED$/\1/p' | sort -u)"
  if [ -z "$graph" ]; then
    echo "::error::no task graph could be read from \`$gradlew $1 --dry-run $gradle_flags\`" >&2
    return 1
  fi
  printf '%s\n' "$graph"
}

local_gate="$(graph_of check | grep -v '^:check$')"
ci_gate="$(graph_of "$ci_tasks")"
missing="$(comm -23 <(printf '%s\n' "$local_gate") <(printf '%s\n' "$ci_gate") || true)"

{
  echo "### Gate coverage"
  echo
  echo "\`check\` expands to $(printf '%s\n' "$local_gate" | wc -l | tr -d ' ') tasks besides itself."
  echo "The workflow's \`GATE_TASKS\` expand to $(printf '%s\n' "$ci_gate" | wc -l | tr -d ' ') tasks."
} >>"$summary"

if [ -n "$missing" ]; then
  echo "::error file=$workflow::the gate workflow does not run every task the local check runs"
  echo "Tasks in check that no CI job asks for:"
  printf '%s\n' "$missing" | sed 's/^/  - /'
  {
    echo
    echo "**Tasks in \`check\` that no CI job asks for:**"
    printf '%s\n' "$missing" | sed 's/^/- /'
    echo
    echo "A contributor green on \`./gradlew check\` could be red in CI, or the reverse."
  } >>"$summary"
  exit 1
fi

echo "Every task in the local check graph is reached by some job in $workflow."
{
  echo
  echo "Every task in the local \`check\` graph is reached by some job in \`$workflow\`."
} >>"$summary"
