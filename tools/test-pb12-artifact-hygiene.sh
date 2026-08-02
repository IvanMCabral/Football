#!/usr/bin/env bash
set -Eeuo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "PB12 artifact hygiene negative test skipped: jq is unavailable"
  exit 0
fi

run_dir="${RUNNER_TEMP:-target}/pb12-hygiene-self-test-$$"
rm -rf -- "$run_dir"
trap 'rm -rf -- "$run_dir"' EXIT

set +e
PB12_RUN_DIR="$run_dir" PB12_HYGIENE_SELF_TEST=true PB12_FORCE_AUTH_TMP_DELETE_FAILURE=true \
  bash tools/run-pb12-docker-smoke.sh >/dev/null 2>&1
exit_code=$?
set -e

[[ "$exit_code" -ne 0 ]]
[[ -s "$run_dir/pb12-docker-smoke-result.json" ]]
jq -e '.status == "FAIL" and .authTempExists == true and .authTempCleanupVerified == false and .finalArtifactSecretScanPassed == false' \
  "$run_dir/pb12-docker-smoke-result.json" >/dev/null
echo "PB12 artifact hygiene negative test passed"
