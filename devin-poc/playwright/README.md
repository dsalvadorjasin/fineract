# Playwright maker/checker flows

This standalone project drives the Mifos web-app against the seeded Fineract
PoC stack. It uses Chromium headlessly, one worker, serial execution, and no
retries.

## Install and run

From this directory:

```bash
npm ci
npx playwright install chromium
npm test
npx tsc --noEmit
```

The tests expect the stack at `http://localhost:4200` and the Fineract API at
`http://localhost:8080/fineract-provider/api/v1`. The generated seed IDs must
exist at `../seed/out/ids.env`; run `../seed/seed.sh` if that file is missing.

Required environment variables are:

- `FINERACT_ADMIN_PASSWORD`
- `POC_MAKER_PASSWORD`
- `POC_CHECKER_PASSWORD`

Secrets are read from the environment and are not logged or stored by this
project.

## Authentication and discovered UI

The app login page is `/#/login`; tests use the username and password fields
with placeholders `Enter your username` and `Enter your password`, followed
by the exact `Login` button. Login lands at `/#/home`.

The relevant seeded account page is
`/#/clients/<CLIENT_ID>/savings-accounts/<SAVINGS_ID>/transactions`. The
`Savings Account Actions` menu contains `Withdrawal`. Checker work is at
`/#/checker-inbox-and-tasks/checker-inbox`; select a row with its
`Select checker item <id>` checkbox, then use `Approve` or `Reject` and
confirm.

The Mifos app keeps its relevant credentials in session storage under
`mifosXCredentials`, rather than in local storage. Playwright storage-state
reuse is therefore not used; each test logs in directly. The app can also
show a stale `Session Timeout` dialog before login and displays a successful
login notification plus an authorization warning afterward. The login helper
dismisses these non-blocking dialogs.

The withdrawal form requires a payment type even though the field is not
obvious from its label. The first available option, `Money Transfer`, is used
by the seeded setup. Amount entry may display a leading zero (`0100`) while
remaining numerically valid.

## Evidence

Explicit key-state screenshots are written to:

```text
../evidence/playwright/<test-name>-<step>.png
```

Playwright failure artifacts remain under `test-results/`, which is ignored.
The checked-in evidence consists only of the small screenshots from a final
passing run.
