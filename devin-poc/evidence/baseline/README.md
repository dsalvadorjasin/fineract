# Phase 3 baseline run

The snapshots below were captured in order on 2026-09-09. Transaction counts
are the rows returned by the savings-account transaction query in each
snapshot. Pending counts are the rows returned by the pending
maker-checker query.

| Step | Operation | Wall time | Result | Snapshot |
| --- | --- | ---: | --- | --- |
| 1 | Reset and seed | 70.93 s | Succeeded | [`baseline-01-seed/db-state.txt`](baseline-01-seed/db-state.txt) |
| 2 | Playwright maker/checker flows | 16.60 s | 3 passed, 1 skipped | [`baseline-02-playwright/db-state.txt`](baseline-02-playwright/db-state.txt) |
| 3 | Savings integration tests | 78.02 s | 6 passed, 0 failed, 0 skipped | [`baseline-03-itests/db-state.txt`](baseline-03-itests/db-state.txt) |
| 4 | Reset and seed | 70.97 s | Succeeded | [`baseline-04-reset/db-state.txt`](baseline-04-reset/db-state.txt) |

## Observed snapshot values

| Snapshot | Balance | Savings transaction count | Pending commands | Maker-checker |
| --- | ---: | ---: | ---: | --- |
| `baseline-01-seed` | 10000.000000 | 1 | 0 | enabled |
| `baseline-02-playwright` | 9900.000000 | 2 | 0 | enabled |
| `baseline-03-itests` | 0.000000 | 3 | 0 | disabled |
| `baseline-04-reset` | 10000.000000 | 1 | 0 | enabled |

The `baseline-03-itests` snapshot shows savings account status `600` and
maker-checker configuration value `0`. Its journal debit and credit totals
were both `20000.000000`.

Evidence files:

- [`playwright.log`](playwright.log)
- [`itests.log`](itests.log)
- [`itests-report/`](itests-report/)

Execution note: the first Playwright pass completed before the baseline log
directory existed, so a recovery reset took 71.08 seconds before the captured
Playwright pass. The step 1 snapshot above was retained from the original
70.93-second reset.
