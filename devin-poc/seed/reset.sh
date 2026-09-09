#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

docker compose -f "$SCRIPT_DIR/../docker-compose.yml" down -v
docker compose -f "$SCRIPT_DIR/../docker-compose.yml" up -d
wait_for_health
"$SCRIPT_DIR/seed.sh"
