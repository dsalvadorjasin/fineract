# Pilot evidence report — configurable maximum single savings withdrawal amount

## 0. Summary

| Item | Value |
| --- | --- |
| PR | (pending) |
| Commit range | `a1f82e9ff6` plus this pilot evidence/spec change |
| Session / VM size | 8 vCPU, 31 GiB RAM, 124 GiB root disk |
| Total wall time (implement → report) | Not measured as one continuous timer |
| Result | partial |

The pilot API and UI flows passed. The all-default savings integration harness
executed 7 tests with 6 passing and 1 failing because an existing withdrawal
test submits 1000 while the pilot limit is 500.

## 1. Change

- Files touched:
  - Core configuration: the approved Phase 4 configuration constant and
    configuration service accessors.
  - Liquibase: changeset `0254` and tenant changelog include.
  - Savings service: maximum-withdrawal domain exception and validation in the
    regular withdrawal path.
  - Tests: unit coverage, `SavingsMaxSingleWithdrawalTest`, and the pilot
    Playwright negative test.
  - Evidence: all files under this `pilot/` directory and the committed
    Playwright screenshots.
- Design notes / deviations from `CHANGE_REQUEST.md`:
  - No production design deviation was made.
  - The EOD job was executed, but no interest transaction appeared because the
    account and business date were both current-day state; business-date
    configuration was disabled and was not changed.
  - The existing all-default integration harness was run with the 500 limit
    enabled. One existing test failed on its intentional 1000 withdrawal; the
    harness and test were not changed for this evidence run.

## 2. Build and deploy

| Step | Command | Wall time | Result |
| --- | --- | --- | --- |
| Jib image | `./gradlew :fineract-provider:jibDockerBuild -x test` | ~28 s | passed |
| Redeploy | `docker compose -f devin-poc/docker-compose.yml up -d fineract` | not separately timed | Fineract healthy |
| Health | `curl .../actuator/health` | not separately timed | `{"status":"UP","groups":["liveness","readiness"]}` |

Liquibase evidence is in [`liquibase.txt`](liquibase.txt):

```text
id | filename                                                              | orderexecuted
1  | db/changelog/tenant/parts/0254_add_max_single_withdrawal_amount_savings_configuration.xml | 1995
1  | db/changelog/tenant/parts/0146_add_final_constraints.xml                                  | 1994
wc-loan-origination-009 | db/changelog/tenant/module/loanorigination/parts/0005_wc_loan_originator_mapping.xml | 1993
```

## 3. Tests

| Suite | Command | Passed / failed / skipped | Wall time |
| --- | --- | --- | --- |
| Unit | `./gradlew :fineract-provider:test --tests org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformServiceJpaRepositoryImplTest` | 12 / 0 / 0 | ~54 s |
| Integration (new test class) | `bash devin-poc/test/run-savings-itests.sh --tests org.apache.fineract.integrationtests.SavingsMaxSingleWithdrawalTest` | 1 / 0 / 0 | ~34 s |
| `run-savings-itests.sh` | `GRADLE_INIT_SCRIPT=/home/ubuntu/fineract-maven-mirror.init.gradle bash devin-poc/test/run-savings-itests.sh` | 6 / 1 / 0 | ~58 s |
| Playwright (full, incl. negative spec), run 1 | `npx playwright test` | 4 / 0 / 0 | ~22 s |
| Playwright (full, incl. negative spec), run 2 | `npx playwright test` | 4 / 0 / 0 | ~21 s |
| TypeScript | `npx tsc --noEmit` | passed | not separately timed |

Console and report evidence:

- Playwright: [`playwright.log`](playwright.log)
- New integration test: [`itest-new.log`](itest-new.log)
- All-default integration tests: [`itests.log`](itests.log)
- All-default Gradle report: [`itests-report/`](itests-report/)

## 4. Functional evidence

### 4.1 Configuration

- After reset, before enabling:
  - `id=76`
  - `name=max-single-withdrawal-amount-savings`
  - `enabled=false`
  - `value=NULL`
  - Evidence: [`pilot-before/db-state.txt`](pilot-before/db-state.txt),
    [`sql/config-before.txt`](sql/config-before.txt)
- After API enable:
  - `id=76`
  - `enabled=true`
  - `value=500`
  - Evidence: [`api/config-get.txt`](api/config-get.txt),
    [`api/config-enable.txt`](api/config-enable.txt),
    [`sql/config-enabled.txt`](sql/config-enabled.txt)
- UI evidence: [`../playwright/ui-config.png`](../playwright/ui-config.png)

### 4.2 Below-limit withdrawal (happy path)

- Maker API withdrawal of 100 returned HTTP 200 with `commandId=24`.
  Evidence: [`api/withdrawal-100-maker.txt`](api/withdrawal-100-maker.txt)
- Checker approval returned HTTP 200. Evidence:
  [`api/withdrawal-100-approve.txt`](api/withdrawal-100-approve.txt)
- After approval:
  - Balance: `9900.000000`
  - New withdrawal transaction: amount `100.000000`
  - Journal: Dr `POC-2001` / Cr `POC-1001`, amount `100.000000`
  - Debit and credit totals balanced
  - Evidence: [`pilot-after-approve/db-state.txt`](pilot-after-approve/db-state.txt)
- UI screenshots:
  - Maker submit: [`../playwright/maker-withdrawal-withdrawal-complete.png`](../playwright/maker-withdrawal-withdrawal-complete.png)
  - Checker inbox: [`../playwright/checker-approve-pending-command.png`](../playwright/checker-approve-pending-command.png)
  - Checker approval: [`../playwright/checker-approve-approved.png`](../playwright/checker-approve-approved.png)

### 4.3 Above-limit withdrawal (negative path)

- Maker API withdrawal of 600 returned HTTP 403.
- Error code:
  `error.msg.savingsaccount.transaction.withdrawal.exceeds.max.single.amount`
- Default message:
  `Withdrawal amount 600 exceeds the maximum permitted single withdrawal of 500`
- Evidence: [`api/withdrawal-600-maker-rejected.txt`](api/withdrawal-600-maker-rejected.txt)
- After the rejected attempt:
  - No new savings transaction row was present.
  - No new journal rows were present.
  - Command-source row `id=25` had `status=5` and
    `result_status_code=403`.
  - No status-2 awaiting-approval row was present.
  - Evidence: [`pilot-after-reject/db-state.txt`](pilot-after-reject/db-state.txt),
    [`sql/command-source.txt`](sql/command-source.txt)
- UI error screenshot:
  [`../playwright/negative-max-withdrawal-error.png`](../playwright/negative-max-withdrawal-error.png)

### 4.4 Unaffected paths

- Deposit of 600 by the maker returned HTTP 200 with resource ID 4.
  Evidence: [`api/deposit-600-maker.txt`](api/deposit-600-maker.txt)
- The new integration test also covered a 600 withdrawal after disabling the
  configuration and passed. Evidence: [`itest-new.log`](itest-new.log).

### 4.5 EOD interest posting

- Job `Post Interest For Savings` was found as job ID 6.
- `POST /jobs/6?command=executeJob` returned HTTP 202.
- Run history returned status `success` with start
  `2026-09-09T12:58:56.210Z` and end `2026-09-09T12:58:56.250Z`.
- Evidence: [`api/eod-job.txt`](api/eod-job.txt)
- No interest transaction appeared in [`pilot-after-eod/db-state.txt`](pilot-after-eod/db-state.txt).
- `enable-business-date` was present with `enabled=false`; it was not changed.
- The account was opened on the current business date, so no elapsed posting
  period was observed.

## 5. Database state snapshots

| Label | When | Evidence |
| --- | --- | --- |
| `pilot-before` | after reset, before enabling the configuration | [`pilot-before/db-state.txt`](pilot-before/db-state.txt) |
| `pilot-after-approve` | after the 100 withdrawal was approved | [`pilot-after-approve/db-state.txt`](pilot-after-approve/db-state.txt) |
| `pilot-after-reject` | after the 600 withdrawal was rejected | [`pilot-after-reject/db-state.txt`](pilot-after-reject/db-state.txt) |
| `pilot-after-eod` | after the interest job completed | [`pilot-after-eod/db-state.txt`](pilot-after-eod/db-state.txt) |
| `pilot-final-reset` | after the final reset/seed | [`pilot-final-reset/db-state.txt`](pilot-final-reset/db-state.txt) |

The final reset restored balance `10000.000000`, one deposit transaction,
balanced journal totals, zero pending commands, and maker-checker enabled.

## 6. Failures and diagnosis

- **Maven Central HTTP 429 — environment.** Part A dependency resolution
  returned `Received status code 429 from server: Too Many Requests`.
  The repository mirror init script was supplied; the required builds and
  focused tests then completed.
- **Balance assertion scale mismatch — code (test).** The new integration
  test compared `9400` to the API's `9400.000000`. The assertion was changed
  to numeric `BigDecimal.compareTo`; the focused test then passed. This is the
  required Part A test-fix entry.
- **Node/npm unavailable in an uninitialized shell — environment.** `node`
  and `npx` were not on PATH. The existing `/home/ubuntu/.nvm/nvm.sh` was
  sourced and Node 22 was selected; TypeScript and Playwright then ran.
- **Initial EOD polling shape mismatch — code (evidence script).** The
  run-history response uses `pageItems`, not `content`; the job itself had
  already completed successfully. The final evidence records the successful
  `pageItems` response.
- **Initial UI error screenshot timing — code (test).** The first capture
  occurred while the submit dialog still displayed `Submitting...`. A
  one-second post-error wait was added, the focused test rerun, and the final
  screenshot visibly contains the error snackbar.
- **All-default integration harness — code (test interaction).**
  `SavingsAccountBalanceCheckAfterReversalTest.testSavingsBalanceWithOverDraftAfterWithdrawal`
  failed because it submits a 1000 withdrawal while the enabled pilot limit
  is 500. The run completed 6 of 7 tests successfully; no harness or
  production change was made to hide this interaction.

## 7. FLEXCUBE mapping check

| FLEXCUBE step | Fineract equivalent exercised | Worked? |
| --- | --- | --- |
| PL/SQL build + deploy | Jib + compose redeploy | Yes |
| DB migration | Liquibase changeset | Yes |
| Maker/checker on CASA withdrawal | `m_portfolio_command_source` flow | Yes |
| GL posting | `acc_gl_journal_entry` | Yes |
| EOD | Post Interest For Savings job | Job completed; no interest posted for current-day account |
| Screen regression | Playwright suite | Yes, 4 passed twice |
| SQL verification tooling | psql | Yes |
