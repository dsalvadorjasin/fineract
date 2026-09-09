#!/usr/bin/env bash
# Configures DBeaver CE for the PoC stack without any GUI interaction:
#   * a read-only PostgreSQL connection "fineract-poc (localhost)" to
#     localhost:5432/fineract_default authenticating via ~/.pgpass
#   * the PostgreSQL JDBC driver wired to a local jar (Maven Central is
#     rate limited from the PoC VM, so DBeaver's automatic driver download
#     fails with HTTP 429)
#   * ~/.pgpass entry for user root using $POSTGRES_PASSWORD
#
# Idempotent: re-running overwrites the same files. Run while DBeaver is
# closed; it rewrites these files on exit.
set -euo pipefail

: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}"

DBEAVER_DATA=${DBEAVER_DATA:-$HOME/.local/share/DBeaverData}
WORKSPACE=$DBEAVER_DATA/workspace6
PROJECT_DIR=$WORKSPACE/General/.dbeaver
CONFIG_DIR=$WORKSPACE/.metadata/.config
DRIVER_DIR=$DBEAVER_DATA/drivers/local
PG_USER=root

if pgrep -x dbeaver >/dev/null 2>&1 || pgrep -f 'dbeaver/dbeaver' >/dev/null 2>&1; then
    echo "DBeaver is running; close it first (it rewrites its config on exit)." >&2
    exit 1
fi

# --- JDBC driver jar --------------------------------------------------------
mkdir -p "$DRIVER_DIR"
jar=$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.postgresql/postgresql" \
        -name 'postgresql-*.jar' ! -name '*sources*' ! -name '*javadoc*' 2>/dev/null \
      | sort -V | tail -1 || true)
if [[ -z "$jar" ]]; then
    echo "postgresql JDBC jar not found in the Gradle cache; run a Gradle build first" >&2
    exit 1
fi
cp -f "$jar" "$DRIVER_DIR/postgresql.jar"

mkdir -p "$CONFIG_DIR"
cat >"$CONFIG_DIR/drivers.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<drivers>
	<provider id="postgresql">
		<driver id="postgres-jdbc" categories="sql" name="PostgreSQL" class="org.postgresql.Driver" url="jdbc:postgresql://{host}[:{port}]/[{database}]" port="5432" defaultDatabase="postgres" defaultUser="postgres" description="PostgreSQL standard driver" custom="false">
			<library type="jar" path="maven:/org.postgresql:postgresql:RELEASE" custom="false" disabled="true"/>
			<library type="jar" path="maven:/net.postgis:postgis-jdbc:RELEASE" custom="false" disabled="true" ignore-dependencies="true"/>
			<library type="jar" path="maven:/net.postgis:postgis-geometry:RELEASE" custom="false" disabled="true" ignore-dependencies="true"/>
			<library type="jar" path="maven:/com.github.waffle:waffle-jna:RELEASE" custom="false" disabled="true"/>
			<library type="license" path="licenses/external/pg.txt" custom="false"/>
			<library type="jar" path="${drivers_home}/local/postgresql.jar" custom="true"/>
		</driver>
	</provider>
</drivers>
EOF

# --- connection profile ----------------------------------------------------
mkdir -p "$PROJECT_DIR"
cat >"$PROJECT_DIR/data-sources.json" <<EOF
{
	"folders": {},
	"connections": {
		"postgres-fineract-poc": {
			"provider": "postgresql",
			"driver": "postgres-jdbc",
			"name": "fineract-poc (localhost)",
			"save-password": true,
			"read-only": true,
			"configuration": {
				"host": "localhost",
				"port": "5432",
				"database": "fineract_default",
				"url": "jdbc:postgresql://localhost:5432/fineract_default",
				"user": "$PG_USER",
				"configurationType": "MANUAL",
				"type": "dev",
				"auth-model": "postgres_pgpass"
			}
		}
	}
}
EOF

# --- ~/.pgpass --------------------------------------------------------------
PGPASS=$HOME/.pgpass
touch "$PGPASS"
chmod 600 "$PGPASS"
grep -v "^localhost:5432:\*:$PG_USER:" "$PGPASS" >"$PGPASS.tmp" || true
printf 'localhost:5432:*:%s:%s\n' "$PG_USER" "$POSTGRES_PASSWORD" >>"$PGPASS.tmp"
mv "$PGPASS.tmp" "$PGPASS"
chmod 600 "$PGPASS"

echo "DBeaver configured: connection 'fineract-poc (localhost)' (read-only, pgpass auth), driver $DRIVER_DIR/postgresql.jar"
