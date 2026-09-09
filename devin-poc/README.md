# Devin PoC: Fineract as a FLEXCUBE stand-in

Everything needed to run Apache Fineract (built from this fork) + PostgreSQL +
the Mifos web-app on a single Devin VM, seed it, drive it as maker/checker and
assert on the database. See `CHANGE_REQUEST.md` for the pilot task and
`EVIDENCE_TEMPLATE.md` for the report structure.

## Layout

| Path | Purpose |
|---|---|
| `docker-compose.yml`, `env/fineract-poc.env` | The stack (db :5432, fineract :8080, web-app :4200) |
| `seed/` | `seed.sh` (idempotent API seed), `reset.sh` (wipe + seed), `lib.sh`, `out/ids.env` (generated) |
| `sql/` | `assertions.sql` canned psql queries (`assertions-dbeaver.sql` = plain-SQL twin for GUI tools), `snapshot-state.sh` dumps them to `evidence/` |
| `playwright/` | Maker/checker UI flows against the web-app |
| `dbeaver/` | `setup-dbeaver.sh` (connection profile, local JDBC driver, `~/.pgpass`) + `RUNBOOK.md` for Computer-Use |
| `harness/` | `run-savings-itests.sh` targeted integration tests |

## Credentials

All secrets are Devin secrets exposed as environment variables; nothing is
stored in the repo.

| Variable | Used for |
|---|---|
| `POSTGRES_PASSWORD` | Postgres superuser `root` **and** Fineract DB user `postgres` |
| `FINERACT_ADMIN_PASSWORD` | Built-in superuser `mifos` (Fineract ships with `password`; `seed.sh` rotates it to this value) |
| `POC_MAKER_PASSWORD` / `POC_CHECKER_PASSWORD` | `poc_maker` / `poc_checker` users created by `seed.sh` |

`seed.sh` switches the active password validation policy to policy id `2` (the
policy requiring at least six characters with upper-case, lower-case, and a
digit) before rotating or creating passwords; this matches the PoC secrets
without requiring special characters.

## Build the image

```bash
./gradlew :fineract-provider:jibDockerBuild -x test --no-daemon
docker images fineract   # -> fineract:latest
```

Redeploy after a Java change:

```bash
./gradlew :fineract-provider:jibDockerBuild -x test && docker compose -f devin-poc/docker-compose.yml up -d fineract
```

Liquibase runs on boot, so new changelog parts are applied automatically.

## Start / stop / reset

```bash
cd devin-poc
docker compose up -d                         # start (needs POSTGRES_PASSWORD in env)
docker compose ps                            # fineract must be "healthy"
docker compose logs -f fineract              # follow boot
docker compose down                          # stop, keep data
docker compose down -v                       # stop and wipe the database
seed/reset.sh                                # down -v, up, wait for health, seed
```

## Verify

```bash
curl -s http://localhost:8080/fineract-provider/actuator/health          # {"status":"UP"}
curl -s -u mifos:$FINERACT_ADMIN_PASSWORD -H 'Fineract-Platform-TenantId: default' \
  http://localhost:8080/fineract-provider/api/v1/clients
PGPASSWORD=$POSTGRES_PASSWORD psql -h localhost -U postgres -d fineract_default -c 'select count(*) from m_appuser'
```

Web UI: <http://localhost:4200> (login `mifos` / `$FINERACT_ADMIN_PASSWORD`,
or `password` on a fresh, unseeded database). The web-app talks directly to
`http://localhost:8080` from the browser; Fineract's CORS is open (`*`) in the
`test` profile.

Swagger UI: <http://localhost:8080/fineract-provider/swagger-ui/index.html>

## Key tables (`fineract_default`)

`m_savings_account`, `m_savings_account_transaction`, `acc_gl_journal_entry`,
`m_portfolio_command_source` (maker/checker audit), `c_configuration`,
`m_appuser`, `m_role`, `m_permission`, `databasechangelog`.

## Not covered

Oracle Forms, WebLogic and PL/SQL tooling have no analogue here; DBeaver via
Computer Use is a capability demo only.
