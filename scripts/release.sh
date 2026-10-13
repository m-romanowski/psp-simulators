#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/release.sh <major|minor|patch> [--repo-root <path>]
  scripts/release.sh --version <x.y.z> [--repo-root <path>]

Bumps this repo's SemVer version, commits, and tags. Everything is derived from the checked-out
repo itself: the version is the bare "version=x.y.z" line of <repo-root>/gradle.properties, which
is the one place this build states it and the one place Gradle reads it from.

A "-SNAPSHOT" suffix, if present, is stripped before bumping. The next version is written back to
gradle.properties, committed ("chore: release v<version>") and tagged ("v<version>"). Nothing is
pushed: the caller decides whether and when to push the commit and the tag.

The resulting version, without the "v" of the tag, is the last line of stdout, for a calling
workflow to capture. Everything else this script says goes to stderr.
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bump_type=""
explicit_version=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    major | minor | patch)
      bump_type="$1"
      shift
      ;;
    --version)
      explicit_version="${2:-}"
      shift 2
      ;;
    --repo-root)
      repo_root="${2:-}"
      shift 2
      ;;
    --help | -h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$bump_type" && -z "$explicit_version" ]]; then
  echo "Missing required argument: a bump type (major|minor|patch) or --version <x.y.z>." >&2
  usage >&2
  exit 1
fi

properties_file="$repo_root/gradle.properties"
if [[ ! -f "$properties_file" ]]; then
  echo "gradle.properties not found at $properties_file" >&2
  exit 1
fi

current_line="$(grep -E '^version=' "$properties_file" || true)"
if [[ -z "$current_line" ]]; then
  echo "No 'version=' line found in $properties_file" >&2
  exit 1
fi
current_version="${current_line#version=}"
current_version="${current_version%-SNAPSHOT}"

if [[ -n "$explicit_version" ]]; then
  next_version="$explicit_version"
else
  IFS='.' read -r major minor patch <<< "$current_version"
  case "$bump_type" in
    major)
      next_version="$((major + 1)).0.0"
      ;;
    minor)
      next_version="$major.$((minor + 1)).0"
      ;;
    patch)
      next_version="$major.$minor.$((patch + 1))"
      ;;
  esac
fi

if ! [[ "$next_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Computed version '$next_version' is not a valid MAJOR.MINOR.PATCH SemVer string." >&2
  exit 1
fi

if git -C "$repo_root" rev-parse --verify --quiet "refs/tags/v$next_version" > /dev/null; then
  echo "This repo already carries the tag v$next_version, so that version was released once." >&2
  exit 1
fi

if [[ -n "$(git -C "$repo_root" status --porcelain)" ]]; then
  echo "The working tree is not clean, and a release commit carries the version and nothing else." >&2
  exit 1
fi

sed -i.bak "s/^version=.*/version=$next_version/" "$properties_file"
rm -f "$properties_file.bak"

git -C "$repo_root" add gradle.properties
git -C "$repo_root" commit -m "chore: release v$next_version" >&2
git -C "$repo_root" tag "v$next_version" >&2

echo "Released v$next_version (was $current_version)" >&2
echo "$next_version"
