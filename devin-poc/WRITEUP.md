# PoC write-up: Apache Fineract as a FLEXCUBE stand-in for Devin

Goal of the PoC: show that the loop **implement → deploy → seed → drive UI as
maker/checker → assert DB → regress → report** can be run end to end by Devin
against a core-banking-shaped system, using Fineract where FLEXCUBE is not
available. VM used throughout: 8 vCPU, 31 GiB RAM, 124 GiB disk (Devin Cloud).

## What was built

| PR | Content |
| --- | --- |
| #1 | Foundation: Jib image, compose stack (Postgres 18.3 / Fineract / Mifos web-app), env, README, blueprint |
| #2 | W1: idempotent API seed + reset, SQL assertions, DB snapshots |
| #3 | W2: Playwright maker/checker flows (approve, reject, negative placeholder) |
| #4 | W3: DBeaver install/profile + Computer-Use runbook and screenshots |
| #5 | W4: targeted savings integration-test harness against the running stack |
| #6 | Phase 3: integration of W1–W4, baseline run, `CHANGE_REQUEST.md`, `EVIDENCE_TEMPLATE.md` |
| #7 | Phase 4: pilot change (configurable max single withdrawal) + full evidence report |

## Success criteria → outcome

| Criterion | Outcome | Evidence |
| --- | --- | --- |
| Build and deploy a code change | Yes. Jib rebuild ~28 s incremental (first build minutes, dominated by dependency download), `compose up -d fineract` redeploy; Liquibase changeset applied on boot | `evidence/pilot/liquibase.txt` |
| Reproducible seed | Yes. `reset.sh` ~71 s, identical `ids.env` shape and assertion output across runs | `evidence/baseline/` |
| Drive the UI as maker and checker | Yes. Playwright: maker withdrawal → pending → checker approve/reject; 4/4 green twice | `evidence/pilot/playwright.log`, `evidence/playwright/*.png` |
| Assert DB state | Yes. psql snapshots + DBeaver GUI run; journal Dr/Cr balanced; command-source audit trail inspected | `sql/assertions.sql`, `evidence/dbeaver/` |
| Regress | Partially. Targeted savings itests run in ~1–3 min. Finding: the enabled pilot limit breaks an upstream test that withdraws 1000 (diagnosed to config, not code) | `evidence/pilot/itests.log` |
| EOD | Job executes on demand (`Post Interest For Savings`, status success). No interest posted on a same-day account with business-date disabled — expected, documented | `evidence/pilot/api/eod-job.txt` |
| Report | `EVIDENCE_TEMPLATE.md` filled as `evidence/pilot/REPORT.md` | |

## What transferred from the FLEXCUBE picture

- **Deploy path**: PL/SQL build + WebLogic deploy ↔ Gradle/Jib + compose redeploy. Same shape (build artefact, restart, schema migration on boot).
- **Schema migration**: Liquibase changesets play the role of DB patch scripts and are verifiable from `databasechangelog`.
- **Maker/checker**: Fineract's command pipeline is a real four-eyes flow with an audit table (`m_portfolio_command_source`). One subtlety found: handlers execute before queueing, so validation errors are recorded as `ERROR` rows rather than never appearing — a domain-rule rejection therefore *does* leave an audit trace, which is arguably what a bank wants.
- **GL posting**: `acc_gl_journal_entry` with product-to-GL mapping; balance checks are plain SQL.
- **EOD**: scheduler jobs callable via REST; equivalent to running an EOD batch.
- **Screen regression**: Playwright against the Angular web-app was stable across runs with role/placeholder selectors.
- **Diagnosis**: the one regression failure was correctly attributed to configuration state, not to the change.

## What did not transfer

- **Oracle Forms**: no equivalent. DBeaver via Computer Use proves the desktop-automation *capability* (launch, connect, run SQL, read result grid) but not Forms-specific interaction.
- **PL/SQL**: Fineract has no stored-procedure business logic; the change lived in Java. Tooling for PL/SQL diff/deploy is untested.
- **WebLogic**: not exercised (Spring Boot container instead).
- **Multi-tenant/branch date**: `enable-business-date` left off; interest posting across dates not demonstrated.

## Timings (this VM)

| Step | Time |
| --- | --- |
| Jib incremental rebuild | ~28 s |
| `reset.sh` (compose down -v, up, health, seed) | ~71 s |
| Playwright suite (4 specs) | ~21 s |
| Targeted itests (2 classes) | 78–180 s |
| New itest class alone | ~34 s |
| Unit test class | ~54 s |

## Operational lessons

1. Upstream savings itests close **every** active savings account and reset
   global configuration: order is itests → `reset.sh` → UI/SQL evidence, never
   the reverse.
2. Maven Central rate-limits (HTTP 429) hit both Gradle and DBeaver's driver
   download on this VM; a Gradle mirror init script and a local JDBC jar from
   the Gradle cache were needed. Both are captured in the blueprint/harness.
3. Fineract's default password policy rejects typical generated secrets; the
   seed switches to policy 2 before creating users.
4. `c_configuration.value` is a `Long`, so monetary limits are whole currency
   units — fine for a pilot, a real change would add a decimal column.
5. The plan intended Phase 4 to run in a fresh session with only
   `CHANGE_REQUEST.md` as input. Here it ran in the orchestrator session; the
   change request is written so a fresh session can still be used to repeat it.
