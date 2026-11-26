#!/usr/bin/env bash
# Fails if the APK asks for any permission that the privacy policy (PRIVACY.md) does not
# allow. Photon needs none; in particular it must never get INTERNET.
# Usage: scripts/check-permissions.sh path/to/app.apk
set -euo pipefail

APK=$1
AAPT2=${AAPT2:-$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)}
# Android adds this one to every app built for API 33+; it is not visible to anyone else.
ALLOWED='\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$'

perms=$("$AAPT2" dump permissions "$APK" | awk '/^uses-permission:/ { gsub(/name=|'\''/, "", $2); print $2 }')
echo "Permissions in $(basename "$APK"):"
echo "${perms:-  (none)}"
unexpected=$(grep -Ev "$ALLOWED" <<<"$perms" || true)
if [[ -n "$unexpected" ]]; then
  echo "Unexpected permissions (update PRIVACY.md before allowing any):"
  echo "$unexpected"
  exit 1
fi
