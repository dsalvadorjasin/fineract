#!/usr/bin/env bash
set -euo pipefail

# Runs selected integration-tests against the already-running HTTP compose stack.
#
# integration-tests/build.gradle wires its normal `test` task to cargoStartLocal,
# waitForFineract, and cargoStopLocal. The PoC stack is already serving Fineract
# on HTTP :8080, so -PcargoDisabled disables those dependencies and finalizer;
# it does not change the test classes or their REST configuration.
#
# Default coverage is intentionally narrow. ClientSavingsIntegrationTest has 45
# tests and creates a broad set of products/configuration; select it explicitly
# only when that larger suite is wanted. When --tests filters are supplied, they
# replace the defaults so a focused class can be run without the destructive
# default set.
#
# These repository tests are not tenant-isolated: FeignSavingsTestBase installs
# FeignSavingsLifecycleExtension, whose cleanup closes every active savings
# account with withdrawBalance=true. SavingsAccountTransactionTest also resets
# global configurations to repository defaults after each test. Do not run the
# default set against a seeded pilot database when preserving its state matters;
# use an isolated database/tenant or restore the seed afterward.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

: "${FINERACT_ADMIN_PASSWORD:?FINERACT_ADMIN_PASSWORD must be set}"

JAVA_HOME="${JAVA_HOME:-/home/ubuntu/.jdks/zulu25}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "Java 25 not found at $JAVA_HOME; install it or set JAVA_HOME" >&2
    exit 1
fi

if ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"25([.]|")'; then
    echo "JAVA_HOME must point to Java 25: $JAVA_HOME" >&2
    exit 1
fi

if (( $# % 2 != 0 )); then
    echo "Usage: $0 [--tests <Gradle test pattern> ...]" >&2
    exit 2
fi

for ((index = 1; index <= $#; index += 2)); do
    if [[ "${!index}" != "--tests" ]]; then
        echo "Only --tests <pattern> arguments are accepted" >&2
        exit 2
    fi
done

for source_file in \
    "$REPO_ROOT/integration-tests/src/test/java/org/apache/fineract/integrationtests/SavingsAccountTransactionTest.java" \
    "$REPO_ROOT/integration-tests/src/test/java/org/apache/fineract/integrationtests/SavingsAccountBalanceCheckAfterReversalTest.java" \
    "$REPO_ROOT/integration-tests/src/test/java/org/apache/fineract/integrationtests/SavingsMaxSingleWithdrawalTest.java"; do
    if [[ ! -f "$source_file" ]]; then
        echo "Configured savings integration test source is missing: $source_file" >&2
        exit 1
    fi
done

if ! health_response=$(curl --fail --silent --show-error \
    "http://localhost:8080/fineract-provider/actuator/health"); then
    echo "Running Fineract stack is not reachable over HTTP at localhost:8080" >&2
    exit 1
fi
if [[ "$(jq -r '.status // empty' <<<"$health_response")" != "UP" ]]; then
    echo "Fineract health endpoint did not report UP on localhost:8080" >&2
    exit 1
fi

export BACKEND_PROTOCOL=http
export BACKEND_HOST=localhost
export BACKEND_PORT=8080
export BACKEND_USERNAME=mifos
export BACKEND_PASSWORD="$FINERACT_ADMIN_PASSWORD"
export BACKEND_TENANT=default

gradle_args=(
    :integration-tests:test
    -PcargoDisabled
    --no-daemon
    --max-workers=2
)

if (($# == 0)); then
    gradle_args+=(
        --tests
        org.apache.fineract.integrationtests.SavingsAccountTransactionTest
        --tests
        org.apache.fineract.integrationtests.SavingsAccountBalanceCheckAfterReversalTest
        --tests
        org.apache.fineract.integrationtests.SavingsMaxSingleWithdrawalTest
    )
fi

if [[ -n "${GRADLE_INIT_SCRIPT:-}" ]]; then
    if [[ ! -f "$GRADLE_INIT_SCRIPT" ]]; then
        echo "GRADLE_INIT_SCRIPT does not exist: $GRADLE_INIT_SCRIPT" >&2
        exit 1
    fi
    gradle_args+=(--init-script "$GRADLE_INIT_SCRIPT")
fi

while (($# > 0)); do
    gradle_args+=(--tests "$2")
    shift 2
done

start_seconds=$SECONDS
if "$REPO_ROOT/gradlew" "${gradle_args[@]}"; then
    status=0
else
    status=$?
fi
elapsed_seconds=$((SECONDS - start_seconds))
printf 'Savings integration tests wall time: %02d:%02d:%02d\n' \
    "$((elapsed_seconds / 3600))" "$(((elapsed_seconds / 60) % 60))" "$((elapsed_seconds % 60))"
exit "$status"
