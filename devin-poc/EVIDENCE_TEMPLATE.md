# Pilot evidence report — <change title>

Copy to `devin-poc/evidence/pilot/REPORT.md` and fill in every section. Where
a section could not be completed, say so and why; do not delete it.

## 0. Summary

| Item | Value |
| --- | --- |
| PR | |
| Commit range | |
| Session / VM size | |
| Total wall time (implement → report) | |
| Result | pass / partial / fail |

## 1. Change

- Files touched (grouped: core config, Liquibase, savings service, tests):
- Design notes / deviations from `CHANGE_REQUEST.md`:

## 2. Build and deploy

| Step | Command | Wall time | Result |
| --- | --- | --- | --- |
| Jib image | `./gradlew :fineract-provider:jibDockerBuild -x test` | | |
| Redeploy | `docker compose -f devin-poc/docker-compose.yml up -d fineract` | | |
| Health | `curl .../actuator/health` | | |

Liquibase (paste query output):

```
SELECT id, filename, orderexecuted FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 3;
```

## 3. Tests

| Suite | Command | Passed / failed / skipped | Wall time |
| --- | --- | --- | --- |
| Unit | | | |
| Integration (new test class) | | | |
| `run-savings-itests.sh` | | | |
| Playwright (full, incl. negative spec) | | | |

Attach or link console logs / Gradle report dirs.

## 4. Functional evidence

### 4.1 Configuration

- `c_configuration` row before/after enabling (id, name, enabled, value):

### 4.2 Below-limit withdrawal (happy path)

- API request/response (status, body excerpt):
- UI screenshots: maker submit (pending), checker inbox, checker approve
- SQL after approve: balance, new transaction row, journal Dr/Cr pair

### 4.3 Above-limit withdrawal (negative path)

- API response: status + `developerMessage` / error code
- UI screenshot: error banner text
- SQL: prove **no** new row in `m_savings_account_transaction` and
  `acc_gl_journal_entry`; `m_portfolio_command_source` shows one `ERROR`
  (status 4) audit row and no `AWAITING_APPROVAL` (status 2) row

### 4.4 Unaffected paths

- Deposit above the limit succeeds (API status + txn row)
- Withdrawal above the limit with configuration **disabled** succeeds

### 4.5 EOD interest posting

- `POST /jobs/{id}?command=executeJob` response
- Interest transaction row + journal entries (Interest Expense Dr / Savings Control Cr)
- Debit total = credit total check

## 5. Database state snapshots

Paths under `devin-poc/evidence/pilot/` produced by
`devin-poc/sql/snapshot-state.sh <label>`:

| Label | When |
| --- | --- |
| `pilot-before` | after `reset.sh`, before enabling the config |
| `pilot-after-approve` | after the below-limit withdrawal was approved |
| `pilot-after-reject` | after the above-limit attempt |
| `pilot-after-eod` | after interest posting |

## 6. Failures and diagnosis

For each failure encountered (even if later fixed): symptom, root cause
classified as **code / data / config / environment**, fix, time lost.

## 7. FLEXCUBE mapping check

| FLEXCUBE step | Fineract equivalent exercised | Worked? |
| --- | --- | --- |
| PL/SQL build + deploy | Jib + compose redeploy | |
| DB migration | Liquibase changeset | |
| Maker/checker on CASA withdrawal | `m_portfolio_command_source` flow | |
| GL posting | `acc_gl_journal_entry` | |
| EOD | Post Interest For Savings job | |
| Screen regression | Playwright suite | |
| SQL verification tooling | psql / DBeaver | |
