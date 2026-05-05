#!/usr/bin/env bash
# kmp-ai — one-shot release setup.
#
# Idempotent. Re-running is safe; existing keys are reused.
#
# What it does:
#   1. Finds (or generates) a signing-capable GPG primary key.
#   2. Exports the private key to a temp file (ASCII-armored).
#   3. Publishes the public half to HKPS keyservers.
#   4. If `gh` CLI is logged in, sets the five required GitHub Actions
#      secrets on fadizg/kmp-ai.
#      Otherwise: prints the values for you to paste manually into
#      https://github.com/fadizg/kmp-ai/settings/secrets/actions
#   5. Optionally tags + pushes a release version, kicking off the
#      Maven Central + xcframework workflow.
#
# Sonatype Central Portal account + user token are NOT created by this
# script — sign up manually at https://central.sonatype.com/account
# (auto-verified via your GitHub login). The script will prompt for
# the username/password tokens it generated for you.

set -euo pipefail

OWNER_REPO="fadizg/kmp-ai"
KEY_FILE="$(mktemp -t kmp-ai-signing.XXXXXX.asc)"
trap 'rm -f "$KEY_FILE"' EXIT

cyan()  { printf '\033[1;36m%s\033[0m\n' "$*"; }
green() { printf '\033[1;32m%s\033[0m\n' "$*"; }
warn()  { printf '\033[1;33m%s\033[0m\n' "$*"; }
err()   { printf '\033[1;31m%s\033[0m\n' "$*" >&2; }

# ── 1. preflight ─────────────────────────────────────────────────────────
command -v gpg >/dev/null || {
    err "gpg not found. Install with: brew install gnupg"
    exit 1
}
HAVE_GH=false
if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
    HAVE_GH=true
else
    warn "gh CLI not authenticated — will print secrets for manual entry."
fi

# ── 2. find or create signing key ────────────────────────────────────────
# Field 5 of `sec:` rows is the long key id; field 12 is capabilities. We
# want a signing-capable primary (lowercase 's' = this exact key signs).
find_signing_key() {
    gpg --list-secret-keys --with-colons 2>/dev/null \
        | awk -F: '/^sec:/ { if ($12 ~ /s/) { print $5; exit } }'
}

PRIMARY_KEY="$(find_signing_key || true)"

if [ -z "$PRIMARY_KEY" ]; then
    cyan "No signing-capable secret key found — generating a new one."
    read -rp "  Real name for the key (e.g. fadizg): " REAL_NAME
    read -rp "  Email associated with the key: " EMAIL
    BATCH="$(mktemp -t kmp-ai-keygen.XXXXXX.batch)"
    cat > "$BATCH" <<EOF
%echo Generating kmp-ai release signing key
Key-Type: RSA
Key-Length: 4096
Subkey-Type: RSA
Subkey-Length: 4096
Name-Real: ${REAL_NAME}
Name-Email: ${EMAIL}
Expire-Date: 5y
%ask-passphrase
%commit
EOF
    # `--batch` is required by gpg ≥ 2.x to read the parameter file.
    # `%ask-passphrase` still triggers pinentry interactively under --batch.
    gpg --batch --gen-key "$BATCH"
    rm -f "$BATCH"
    PRIMARY_KEY="$(find_signing_key || true)"
fi

if [ -z "$PRIMARY_KEY" ]; then
    err "Could not find or generate a signing-capable GPG key."
    exit 1
fi

SHORT_ID="${PRIMARY_KEY: -8}"
green "✓ Using signing key: $PRIMARY_KEY (short id: $SHORT_ID)"

# ── 3. export private half ──────────────────────────────────────────────
gpg --export-secret-keys --armor "$PRIMARY_KEY" > "$KEY_FILE"
SIZE=$(wc -c < "$KEY_FILE" | tr -d ' ')
if [ "$SIZE" -lt 100 ]; then
    err "Private key export produced only $SIZE bytes — something's wrong."
    exit 1
fi
green "✓ Exported $SIZE bytes (deleted on script exit)."

# ── 4. push public half to keyservers ───────────────────────────────────
cyan "Publishing public key to keyservers (HKPS, port 443)…"
PUSH_OK=false
for ks in hkps://keys.openpgp.org hkps://keyserver.ubuntu.com; do
    if gpg --keyserver "$ks" --send-keys "$PRIMARY_KEY" 2>&1 | sed 's/^/  /'; then
        green "  ✓ $ks"
        PUSH_OK=true
    fi
done

if ! $PUSH_OK; then
    warn "All keyserver pushes failed. Use the web UI fallback:"
    warn "  1. gpg --armor --export $PRIMARY_KEY > kmp-ai-public.asc"
    warn "  2. Open https://keys.openpgp.org/upload"
    warn "  3. Upload kmp-ai-public.asc and confirm via the email link."
fi

# ── 5. collect Sonatype creds + passphrase ──────────────────────────────
echo
cyan "Sonatype Central Portal credentials"
echo "  Get them from https://central.sonatype.com/account → Generate User Token"
echo "  (these are token strings, not your GitHub password)"
read -rp "  Username token: " MAVEN_USERNAME
read -rsp "  Password token: " MAVEN_PASSWORD; echo
echo
read -rsp "GPG signing passphrase (the one you set for $PRIMARY_KEY): " SIGNING_PASSWORD; echo

# ── 6. set GitHub secrets ───────────────────────────────────────────────
if $HAVE_GH; then
    cyan "Setting GitHub Actions secrets on $OWNER_REPO via gh CLI…"
    gh secret set MAVEN_CENTRAL_USERNAME -R "$OWNER_REPO" --body "$MAVEN_USERNAME"
    gh secret set MAVEN_CENTRAL_PASSWORD -R "$OWNER_REPO" --body "$MAVEN_PASSWORD"
    gh secret set SIGNING_KEY            -R "$OWNER_REPO" < "$KEY_FILE"
    gh secret set SIGNING_KEY_ID         -R "$OWNER_REPO" --body "$SHORT_ID"
    gh secret set SIGNING_PASSWORD       -R "$OWNER_REPO" --body "$SIGNING_PASSWORD"
    green "✓ All 5 secrets set."
else
    cyan "Paste the following into GitHub manually:"
    echo "  https://github.com/$OWNER_REPO/settings/secrets/actions"
    echo
    printf '  %-26s %s\n' "MAVEN_CENTRAL_USERNAME"  "$MAVEN_USERNAME"
    printf '  %-26s %s\n' "MAVEN_CENTRAL_PASSWORD"  "(the password token you entered)"
    printf '  %-26s %s\n' "SIGNING_KEY_ID"          "$SHORT_ID"
    printf '  %-26s %s\n' "SIGNING_PASSWORD"        "(the GPG passphrase you entered)"
    printf '  %-26s %s\n' "SIGNING_KEY"             "see below ↓"
    echo
    cat "$KEY_FILE"
    echo
fi

# ── 7. optional: tag + push a release ───────────────────────────────────
echo
read -rp "Tag and push a release version now? (e.g. 0.2.0, blank to skip): " VERSION
if [ -n "$VERSION" ]; then
    if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        warn "Version must be N.N.N — skipping tag."
    else
        TAG="v$VERSION"
        cyan "Tagging $TAG and pushing to origin…"
        git tag -a "$TAG" -m "$TAG"
        git push origin "$TAG"
        green "✓ Pushed $TAG. Workflow:"
        echo "  https://github.com/$OWNER_REPO/actions/workflows/release.yml"
    fi
fi

echo
green "Done."
echo "If you skipped the tag step above, when you're ready run:"
echo "  git tag -a v0.2.0 -m 'v0.2.0' && git push origin v0.2.0"
