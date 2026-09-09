# Targeted savings integration-test harness

`run-savings-itests.sh` runs a small, API-driven subset of the repository's
`integration-tests` module against the Fineract instance already running in the
PoC compose stack. It does not start or stop Tomcat, Docker, or the database.

## Run against the running stack

The stack must be healthy on HTTP port 8080. From the repository root:

```bash
devin-poc/test/run-savings-itests.sh
```

The script exports the integration-test backend settings:

```text
BACKEND_PROTOCOL=http
BACKEND_HOST=localhost
BACKEND_PORT=8080
BACKEND_USERNAME=mifos
BACKEND_PASSWORD=$FINERACT_ADMIN_PASSWORD
BACKEND_TENANT=default
```

The password is read from the environment and is never printed by the script.
Java 25 is selected from `/home/ubuntu/.jdks/zulu25` by default. Set
`JAVA_HOME` to another Java 25 installation if needed.

The default test classes are:

- `org.apache.fineract.integrationtests.SavingsAccountTransactionTest`
- `org.apache.fineract.integrationtests.SavingsAccountBalanceCheckAfterReversalTest`

`ClientSavingsIntegrationTest` exists, but contains 45 tests and is a broad,
slow suite rather than a targeted smoke set. Additional test filters are
accepted and are added to the defaults:

```bash
devin-poc/test/run-savings-itests.sh \
  --tests 'org.apache.fineract.integrationtests.SavingsAccountsExternalIdTest'
```

The Gradle task normally depends on `cargoStartLocal` and `waitForFineract` and
finalizes with `cargoStopLocal`. The harness passes `-PcargoDisabled`, which
removes those server lifecycle dependencies so tests use the running HTTP
compose instance. It also uses `--no-daemon --max-workers=2`.

If dependency resolution encounters a Maven Central throttle, an optional
mirror init script can be supplied without changing the default:

```bash
GRADLE_INIT_SCRIPT=/path/to/mirror.init.gradle \
  devin-poc/test/run-savings-itests.sh
```

## Database and configuration caveats

The selected tests create their own clients, savings products, and accounts
through generated/default data. They do not target the seeded PoC client or
account IDs, so they should not collide with the seed and do not require a
fresh database.

`SavingsAccountTransactionTest` exercises transaction dates, concurrent
transactions, batch transactions, and deadlock handling. The reversal-balance
class exercises withdrawal validation and overdraft reversal. These tests use
the shared REST/Feign helpers and tenant configured by the environment.

The broader `ClientSavingsIntegrationTest` explicitly toggles business-date
and savings global configuration values, including backdated-transaction and
post-reversal transaction settings, and restores several values in its test
cleanup. Do not assume a larger custom selection leaves global configuration
unchanged; run `devin-poc/seed/seed.sh` afterward if a selected test changes
the pilot's configuration. The targeted default classes do not intentionally
toggle the maker-checker configuration in their test bodies. However, the
repository's `FeignSavingsLifecycleExtension` runs before and after each test
and closes **every** active savings account with `withdrawBalance=true`; that
includes the seeded PoC account. `SavingsAccountTransactionTest` also runs
`resetAllDefaultGlobalConfigurations()` after each test, which changes the
PoC's `maker-checker` configuration back to the repository default (`enabled =
false`). Consequently, the default set is suitable for an isolated test
database/tenant, not for a seeded pilot database whose state must remain
untouched. A fresh or isolated database is required unless the upstream
lifecycle cleanup is changed to exclude pilot accounts.

### W4 acceptance result on this VM

The one end-to-end run used the two default classes and completed in
**180.51 seconds (03:00)**:

- **6 passed**
- **0 failed**
- **0 skipped**

The tests themselves create generated clients, savings products, and accounts;
those resources did not collide by ID with the seeded resources. The lifecycle
cleanup did mutate the seeded account, though: the account changed from
`9800.00` and active to `0.00` and closed, with a new `9800.00` withdrawal.
The final journal remained balanced (`20000.00` debit and credit), and pending
maker-checker commands were zero. Maker-checker did not cause a test failure,
but the test cleanup left the global maker-checker configuration disabled.

The acceptance artifacts are:

- Console log:
  `/home/ubuntu/w4-evidence/savings-itests-console.log`
- Gradle HTML report:
  `/home/ubuntu/w4-evidence/gradle-test-report/`
- Post-run SQL snapshot:
  `/home/ubuntu/w4-evidence/db-state.txt`

The snapshot confirms the mutation described above. Re-running `seed.sh` is
appropriate to restore configuration such as maker-checker, but it is
idempotent and does not reopen or re-fund a closed seeded savings account.
Restoring the original `9800.00` seeded state requires the normal reset/seed
procedure in a controlled environment; it was not performed as part of this
W4 run.

The targeted test body that uses `businessDateHelper.runAt(...)` temporarily
enables business date and disables it in a `finally` block. The observed
persistent global change was instead the `maker-checker` reset in
`SavingsAccountTransactionTest` teardown. No GUI actions or stack reset were
performed.

## Fast unit-test path

For a savings unit test without HTTP, database, or Docker:

```bash
./gradlew :fineract-savings:test \
  --tests 'org.apache.fineract.portfolio.savings.domain.SavingsAccountAccountingBridgeTest' \
  --no-daemon --max-workers=2
```

On this VM the command completed successfully in about **2m 48s** (one test).

## Cucumber E2E note

The `fineract-e2e-tests-core` and `fineract-e2e-tests-runner` projects provide a
Cucumber/Allure runner with feature files, extra test dependencies, and a
`cucumber` task that performs formatting checks and generates an Allure report.
That path is not a practical replacement for this small pilot harness: it has
broader feature coverage and reporting/tooling overhead, and was inspected but
not run here.
