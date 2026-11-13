#!/usr/bin/env bash
# Prints the tag for the next release.
#
#   scripts/next-version.sh <patch|minor|major> <stable|beta|nightly>
#
# The bump is applied to the latest stable tag (vX.Y.Z; v0.0.0 if there is none):
#   stable   v1.3.0
#   beta     v1.3.0-beta.N        N counts up across betas of the same version
#   nightly  v1.3.0-nightly.YYYYMMDD.<short sha>
set -euo pipefail

bump=${1:?usage: next-version.sh <patch|minor|major> <stable|beta|nightly>}
channel=${2:?usage: next-version.sh <patch|minor|major> <stable|beta|nightly>}

latest=$(git tag --list 'v*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n1 || true)
IFS=. read -r major minor patch <<<"${latest#v}"
major=${major:-0} minor=${minor:-0} patch=${patch:-0}

case "$bump" in
  major) major=$((major + 1)) minor=0 patch=0 ;;
  minor) minor=$((minor + 1)) patch=0 ;;
  patch) patch=$((patch + 1)) ;;
  *) echo "unknown bump: $bump" >&2; exit 2 ;;
esac
next="v$major.$minor.$patch"

case "$channel" in
  stable)
    if git rev-parse -q --verify "refs/tags/$next" >/dev/null; then
      echo "$next already exists" >&2
      exit 1
    fi
    echo "$next"
    ;;
  beta)
    last=$(git tag --list "$next-beta.*" | sed -E "s/^.*-beta\.//" | grep -E '^[0-9]+$' | sort -n | tail -n1 || true)
    echo "$next-beta.$(( ${last:-0} + 1 ))"
    ;;
  nightly)
    echo "$next-nightly.$(date -u +%Y%m%d).$(git rev-parse --short=7 HEAD)"
    ;;
  *) echo "unknown channel: $channel" >&2; exit 2 ;;
esac
