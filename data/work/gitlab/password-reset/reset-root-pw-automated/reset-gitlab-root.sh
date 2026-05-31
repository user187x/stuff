#!/bin/bash
set -euo pipefail

EXPECT_SCRIPT="$(dirname "$(readlink -f "$0")")/reset-root-password.exp"

echo "Finding Gitlab toolbox pod..."
pod=$(kubectl get pods -l app=toolbox --namespace gitlab \
 -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)

if [[ -z "$pod" ]]; then
 echo "gitlab toolbox pod not found!" >&2
 exit 1
fi

echo "Found pod! -> $pod"

if [[ ! -x "$EXPECT_SCRIPT" ]]; then
 echo "Expect script not executable: $EXPECT_SCRIPT" >&2
 echo "Run: chmod +x \"$EXPECT_SCRIPT\"" >&2
 exit 1
fi

echo "Issuing gitlab-rake command to reset password..."
"$EXPECT_SCRIPT" "$pod"
