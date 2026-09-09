# Change request: configurable maximum single-withdrawal amount on savings accounts

This is the pilot task for the Fineract-as-FLEXCUBE-stand-in PoC. It is
meant to be handed to a fresh session as its only input; everything else the
session needs is in `devin-poc/README.md` and the blueprint knowledge.

## Requirement

Add a global configuration `max-single-withdrawal-amount-savings`
(`c_configuration`), **disabled by default**, whose `value` is the monetary
limit.

When the configuration is enabled, any savings-account withdrawal
(`POST /savingsaccounts/{id}/transactions?command=withdrawal`) whose amount
is strictly greater than the limit must be rejected with a domain-rule error:

| Field | Value |
| --- | --- |
| Error code | `error.msg.savingsaccount.transaction.withdrawal.exceeds.max.single.amount` |
| Default message | `Withdrawal amount {amount} exceeds the maximum permitted single withdrawal of {limit}` |
| HTTP status | whatever `AbstractPlatformDomainRuleException` maps to today (403) — do not invent a new mapping |

Constraints:

1. No `m_savings_account_transaction` row and no `acc_gl_journal_entry` row
   may be written for a rejected attempt.
2. The rule applies whether or not maker-checker is enabled: it must fail at
   **maker submission time**, before the command is queued in
   `m_portfolio_command_source`. A rejected attempt therefore leaves no
   command-source row either.
3. Deposits, account transfers, interest posting, fee/charge withdrawals and
   withdrawals when the configuration is disabled are unaffected.
4. Amount equal to the limit is allowed.
5. Configuration disabled, or enabled with a null/zero/negative value, means
   "no limit".

## Implementation hints (mirror apache/fineract#5465 "force withdrawal")

- `fineract-core/.../infrastructure/configuration/api/GlobalConfigurationConstants.java`
  — new constant `MAX_SINGLE_WITHDRAWAL_AMOUNT_SAVINGS`.
- `fineract-core/.../configuration/domain/ConfigurationDomainService.java` and
  `fineract-provider/.../ConfigurationDomainServiceJpa.java` —
  `isMaxSingleWithdrawalAmountEnabled()` / `retrieveMaxSingleWithdrawalAmount()`.
- New Liquibase part
  `fineract-provider/src/main/resources/db/changelog/tenant/parts/<next-number>_max_single_withdrawal_config.xml`
  inserting the `c_configuration` row (`enabled=false`, `value` null); include
  it from `changelog-tenant.xml`.
- Validation in the withdrawal path
  (`SavingsAccountWritePlatformServiceJpaRepositoryImpl.withdrawal()` or
  `SavingsAccountDomainServiceJpa.handleWithdrawal()`); new exception
  extending `AbstractPlatformDomainRuleException`. Take care that the check
  sits before the maker-checker queueing and before any persistence.
- Integration test in `integration-tests/.../SavingsAccount*Test.java`:
  enable config with a limit, withdraw over the limit → error code above;
  withdraw under and at the limit → success; reset the configuration in
  teardown.

## Runtime workflow the pilot must follow

1. Implement + unit/integration tests.
2. `./gradlew :fineract-provider:jibDockerBuild -x test`, then
   `docker compose -f devin-poc/docker-compose.yml up -d fineract`; confirm
   Liquibase applied the changeset
   (`SELECT id, filename, orderexecuted FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 3`).
3. `devin-poc/seed/reset.sh`; enable the configuration and set the value
   through `PUT /configurations/{id}`.
4. UI (Playwright + screenshots): maker withdrawal below the limit → pending →
   checker approves; maker withdrawal above the limit → error banner with the
   new message. Repeat both via API.
5. SQL: transaction rows, balanced journal entries, maker/checker audit trail
   in `m_portfolio_command_source`, **no row of any kind** for the rejected
   attempt. Use `devin-poc/sql/snapshot-state.sh <label>`.
6. "EOD": run the `Post Interest For Savings` job via `/jobs`, assert the
   interest transaction and its journal entries.
7. Un-skip and complete `devin-poc/playwright/tests/negative-max-withdrawal.spec.ts`;
   run the full Playwright suite and `devin-poc/test/run-savings-itests.sh`
   (the itests close all savings accounts — run them last, or reset after).
8. Fill in `devin-poc/EVIDENCE_TEMPLATE.md` as `devin-poc/evidence/pilot/REPORT.md`
   and open a PR against `devin-poc` with code, tests and evidence.
