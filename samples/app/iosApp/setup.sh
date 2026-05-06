#!/usr/bin/env bash
# Generate iosApp.xcodeproj from project.yml using XcodeGen.
#
# Run once after cloning the repo (or whenever project.yml changes).
# Then: `open iosApp.xcodeproj` in Finder, pick a simulator in Xcode,
# tap Run.
#
# Idempotent — safe to re-run; xcodegen overwrites the project cleanly.

set -euo pipefail

cd "$(dirname "$0")"

if ! command -v xcodegen >/dev/null 2>&1; then
    echo "xcodegen not found. Installing via Homebrew…"
    if ! command -v brew >/dev/null 2>&1; then
        echo "Error: Homebrew not installed. Install from https://brew.sh and re-run." >&2
        exit 1
    fi
    brew install xcodegen
fi

xcodegen generate
echo
echo "✓ iosApp.xcodeproj generated."
echo "  Next: open iosApp.xcodeproj"
