#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_DIR=$(cd -- "$SCRIPT_DIR/../.." && pwd)
LABEL=${1:-$(date -u +%Y%m%dT%H%M%SZ)}
IDS_FILE="$REPO_DIR/devin-poc/seed/out/ids.env"
EVIDENCE_DIR="$REPO_DIR/devin-poc/evidence/$LABEL"

[[ -f "$IDS_FILE" ]] || { echo "Missing $IDS_FILE; run seed.sh first" >&2; exit 1; }
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}"
# shellcheck disable=SC1090
source "$IDS_FILE"
mkdir -p "$EVIDENCE_DIR"

PGPASSWORD=$POSTGRES_PASSWORD psql \
    -h localhost \
    -U postgres \
    -d fineract_default \
    -v "savings_id=$SAVINGS_ID" \
    -f "$SCRIPT_DIR/assertions.sql" >"$EVIDENCE_DIR/db-state.txt"

echo "$EVIDENCE_DIR/db-state.txt"
