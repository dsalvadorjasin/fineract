# DBeaver runbook (Oracle Forms / SQL Developer stand-in)

DBeaver CE is the desktop-tooling stand-in for the FLEXCUBE workflow step
"open a DB tool and check the tables". It is driven through Computer Use
(the Devin desktop), not through an API, so this runbook is written as
click-by-click steps plus the equivalent headless setup.

## Setup

Installed by `.devin/blueprint.yaml` (DBeaver CE `.deb` from
`dbeaver.io/files/dbeaver-ce_latest_amd64.deb`). The connection profile,
JDBC driver and `~/.pgpass` are written by

```bash
POSTGRES_PASSWORD=... bash devin-poc/dbeaver/setup-dbeaver.sh
```

which is idempotent and must run while DBeaver is closed. It produces:

| Item | Value |
| --- | --- |
| Connection name | `fineract-poc (localhost)` |
| Host / port / database | `localhost` / `5432` / `fineract_default` |
| User | `root` (compose `POSTGRES_USER`), password from `~/.pgpass` |
| Mode | read-only (DBeaver `read-only: true`) |
| Driver | local `postgresql` jar copied from the Gradle cache |

Why a local driver jar: DBeaver normally downloads the PostgreSQL driver
from Maven Central on first connect. From the PoC VM Maven Central answers
HTTP 429, so the automatic download fails ("Driver download failed"). The
setup script points the driver definition at the jar Fineract already
pulled into `~/.gradle`.

## Running the assertions in the GUI

1. Launch DBeaver (`dbeaver &` or the desktop launcher). Dismiss the
   "sample database" / tips prompts if shown.
2. In the **Database Navigator** (left) select `fineract-poc (localhost)`.
3. Press **F4** (Edit Connection) -> **Test Connection ...** and confirm
   `Connected`, then **OK**. Evidence: `../evidence/dbeaver/01-connection-test.png`.
4. **File -> Open File...** and open `devin-poc/sql/assertions-dbeaver.sql`
   (paste the absolute path into the GTK dialog with `Ctrl+L`).
5. In the editor toolbar click the **`< N/A >`** active-datasource selector
   (or `Ctrl+9`) and double-click `fineract-poc (localhost)`.
6. Press **Alt+X** (Execute script). Six result tabs appear:
   `m_savings_account` summary, transactions, journal entries, debit/credit
   totals, pending maker-checker commands, and configuration; the
   **Statistics** tab shows `Queries 6`.
7. Take a full-screen screenshot per tab of interest into
   `devin-poc/evidence/dbeaver/`.

`assertions-dbeaver.sql` is the plain-SQL twin of `sql/assertions.sql`: it
drops the psql meta-commands (`\set`, `\echo`) and resolves the account via
`external_id = 'poc-savings-1'` instead of the `:savings_id` variable, so
it runs unchanged in any GUI SQL editor.

## Evidence captured (2026-09-09, after the W2 Playwright runs)

| File | Shows |
| --- | --- |
| `01-connection-test.png` | Connection test: PostgreSQL 18.3, JDBC driver 42.7.11 |
| `02-transactions.png` | 1 deposit of 10,000 + 2 approved withdrawals of 100 |
| `03-journal-entries.png` | Balanced Dr/Cr pairs on `POC-1001` / `POC-2001` |
| `04-statistics.png` | Script run: 6 queries, 0 updated rows |

## Fresh-session reproducibility

Verified in this session by wiping `data-sources.json`, `drivers.xml` and
the local driver dir, re-running `setup-dbeaver.sh`, relaunching DBeaver and
getting `Connected` from **Test Connection** with no manual driver or
credential entry. Everything lives under `~/.local/share/DBeaverData` and
`~/.pgpass`; the blueprint runs the script as a maintenance step after the
Jib build so the JDBC jar is present in the Gradle cache.

Known GUI quirks:

* The first launch shows a one-time product configuration wizard.
* The AI-assistant side panel is open by default; close it to get room.
* Maximize with `wmctrl -r "DBeaver" -b add,maximized_vert,maximized_horz`
  before screenshots.
