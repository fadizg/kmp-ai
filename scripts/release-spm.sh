#!/usr/bin/env bash
# kmp-ai — Swift Package Manager release helper (LOCAL / EMERGENCY only).
#
# Canonical SPM release path is .github/workflows/release.yml: pushing a
# tag triggers Maven Central publish + KmpAI.xcframework build + Package.swift
# patch + tag rewrite + Release upload, all in one job. This script mirrors
# that logic for two cases CI doesn't cover:
#
#   1. Testing the SPM machinery without burning a real version. Run with
#      a throwaway version; inspect Package.swift / the zips before pushing.
#   2. Releasing when CI is down or the runner doesn't have the macOS SDK.
#
# Run on macOS. Builds KmpAI.xcframework, zips both KmpAI.xcframework and
# the cached llama.xcframework deterministically, computes their SHA-256
# checksums, updates Package.swift, commits, tags, pushes — then uploads
# the zips to the GitHub Release.
#
# Usage:
#   ./scripts/release-spm.sh 0.3.0
#
# Prerequisites:
#   - Clean working tree on `main`, with all release-bound commits already
#     pushed (this script will add one more commit + tag).
#   - `gh` CLI authenticated (gh auth status).
#   - `.cache/llama.xcframework` present (run any iOS build first to fetch).

set -euo pipefail

cyan()  { printf '\033[1;36m%s\033[0m\n' "$*"; }
green() { printf '\033[1;32m%s\033[0m\n' "$*"; }
err()   { printf '\033[1;31m%s\033[0m\n' "$*" >&2; }

# ── 1. parse args ────────────────────────────────────────────────────────
if [ $# -ne 1 ]; then
    err "usage: $0 <version>   (e.g. $0 0.3.0)"
    exit 1
fi
VERSION="$1"
TAG="v$VERSION"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    err "version must be N.N.N (got: $VERSION)"
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# ── 2. preflight ─────────────────────────────────────────────────────────
command -v gh >/dev/null || { err "gh CLI not installed (brew install gh)"; exit 1; }
gh auth status >/dev/null 2>&1 || { err "gh not authenticated (gh auth login)"; exit 1; }

if [ -n "$(git status --porcelain)" ]; then
    err "working tree is dirty — commit or stash first"
    git status -s
    exit 1
fi

if [ "$(git rev-parse --abbrev-ref HEAD)" != "main" ]; then
    err "must be on main branch (current: $(git rev-parse --abbrev-ref HEAD))"
    exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
    err "tag $TAG already exists locally — pick a new version or delete it"
    exit 1
fi

# ── 3. build KmpAI.xcframework ───────────────────────────────────────────
cyan "Building KmpAI.xcframework (release variant)…"
./gradlew :llm:assembleKmpAIReleaseXCFramework -Pkmp-ai.android=false

KMPAI_XCF="$REPO_ROOT/llm/build/XCFrameworks/release/KmpAI.xcframework"
LLAMA_XCF="$REPO_ROOT/.cache/llama.xcframework"

[ -d "$KMPAI_XCF" ] || { err "KmpAI.xcframework not found at $KMPAI_XCF"; exit 1; }
[ -d "$LLAMA_XCF" ] || { err "llama.xcframework not found at $LLAMA_XCF — run any iOS build first"; exit 1; }

# ── 4. zip deterministically ─────────────────────────────────────────────
# Strategy: fix all mtimes to 1980-01-01, sort file list lexicographically,
# strip OS-specific extra fields with `-X`. Reproducible across runs.
deterministic_zip() {
    local src_dir="$1"; local out_zip="$2"
    local parent name
    parent="$(dirname "$src_dir")"
    name="$(basename "$src_dir")"
    rm -f "$out_zip"
    (
        cd "$parent"
        find "$name" -exec touch -t 198001010000.00 {} +
        find "$name" -print | LC_ALL=C sort | zip -X -9 "$out_zip" -@
    ) >/dev/null
}

STAGE="$REPO_ROOT/build/spm-release"
rm -rf "$STAGE" && mkdir -p "$STAGE"

cyan "Zipping (deterministic)…"
deterministic_zip "$KMPAI_XCF" "$STAGE/KmpAI.xcframework.zip"
deterministic_zip "$LLAMA_XCF" "$STAGE/llama.xcframework.zip"

# ── 5. compute checksums ─────────────────────────────────────────────────
KMPAI_SUM="$(swift package compute-checksum "$STAGE/KmpAI.xcframework.zip")"
LLAMA_SUM="$(swift package compute-checksum "$STAGE/llama.xcframework.zip")"
green "✓ KmpAI checksum: $KMPAI_SUM"
green "✓ llama checksum: $LLAMA_SUM"

# ── 6. patch Package.swift ───────────────────────────────────────────────
cyan "Updating Package.swift…"
PKG="$REPO_ROOT/Package.swift"
# macOS sed needs `-i ''`. Anchor on the literal line shape so we never
# accidentally rewrite anything else.
sed -i '' \
    -e "s|^let releaseTag = \".*\"|let releaseTag = \"$VERSION\"|" \
    -e "s|^let kmpAIChecksum = \".*\"|let kmpAIChecksum = \"$KMPAI_SUM\"|" \
    -e "s|^let llamaChecksum = \".*\"|let llamaChecksum = \"$LLAMA_SUM\"|" \
    "$PKG"

# Sanity-check the substitution actually happened (caller may have moved the
# anchor lines — we'd silently produce wrong Package.swift otherwise).
grep -q "let releaseTag = \"$VERSION\"" "$PKG"      || { err "Package.swift releaseTag patch failed"; exit 1; }
grep -q "let kmpAIChecksum = \"$KMPAI_SUM\"" "$PKG" || { err "Package.swift kmpAIChecksum patch failed"; exit 1; }
grep -q "let llamaChecksum = \"$LLAMA_SUM\"" "$PKG" || { err "Package.swift llamaChecksum patch failed"; exit 1; }

# ── 7. commit + tag + push ───────────────────────────────────────────────
cyan "Committing Package.swift change…"
git add Package.swift
git commit -m "SPM: $TAG release coordinates"

cyan "Tagging $TAG and pushing…"
git tag -a "$TAG" -m "$TAG"
git push origin main "$TAG"

# ── 8. wait for release.yml to create the Release, then upload zips ──────
# release.yml is triggered by the tag push above. It uses softprops which
# creates the Release on first asset upload. We need to wait until the
# release exists, then attach our two deterministic zips. If softprops has
# already attached its own (non-deterministic) llama.xcframework.zip we
# overwrite it with --clobber so the checksum in Package.swift wins.
cyan "Waiting for GitHub Release v$VERSION to be created by CI…"
for i in $(seq 1 60); do
    if gh release view "$TAG" >/dev/null 2>&1; then
        green "✓ Release exists."
        break
    fi
    sleep 10
done

if ! gh release view "$TAG" >/dev/null 2>&1; then
    err "Release $TAG was not created within 10 minutes."
    err "Upload the zips manually after CI finishes:"
    err "  gh release upload $TAG \\"
    err "    \"$STAGE/KmpAI.xcframework.zip\" \\"
    err "    \"$STAGE/llama.xcframework.zip\" --clobber"
    exit 1
fi

cyan "Uploading deterministic zips (--clobber)…"
gh release upload "$TAG" \
    "$STAGE/KmpAI.xcframework.zip" \
    "$STAGE/llama.xcframework.zip" \
    --clobber

green "Done."
echo
echo "Consumers can now use:"
echo "  .package(url: \"https://github.com/fadizg/kmp-ai\", exact: \"$VERSION\")"
echo "  // then add: .product(name: \"KmpAI\", package: \"kmp-ai\") to target deps"
