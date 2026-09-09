#!/usr/bin/env bash
set -euo pipefail

BASE=http://localhost:8080/fineract-provider/api/v1
TENANT_HEADER="Fineract-Platform-TenantId: default"
HEALTH_URL=http://localhost:8080/fineract-provider/actuator/health
DATE_FORMAT="dd MMMM yyyy"
LOCALE=en
AUTH_USER=mifos
AUTH_PASSWORD=${FINERACT_ADMIN_PASSWORD:-}

api() {
    local method=$1
    local path=$2
    local body=${3:-}
    local response status response_body
    local -a args=(-sS --fail-with-body -u "${AUTH_USER}:${AUTH_PASSWORD}" -H "$TENANT_HEADER" -H "Accept: application/json" -X "$method")

    if [[ -n "$body" ]]; then
        args+=(-H "Content-Type: application/json" --data "$body")
    fi

    response=$(curl "${args[@]}" -w $'\n%{http_code}' "${BASE}${path}") || {
        status=${response##*$'\n'}
        response_body=${response%$'\n'*}
        redact_error "$response_body" >&2
        return 1
    }
    status=${response##*$'\n'}
    response_body=${response%$'\n'*}
    if (( status < 200 || status >= 300 )); then
        redact_error "$response_body" >&2
        return 1
    fi
    printf '%s' "$response_body"
}

redact_error() {
    local response=$1
    if jq -e . >/dev/null 2>&1 <<<"$response"; then
        jq 'if .errors then .errors |= map(if ((.parameterName // "") | test("password"; "i")) then .args = [] else . end) else . end
            | walk(if type == "object" then with_entries(if (.key | test("password"; "i")) then .value = "REDACTED" else . end) else . end)' <<<"$response"
    else
        printf '%s\n' "$response"
    fi
}

wait_for_health() {
    local deadline=$((SECONDS + 300))
    local response

    while (( SECONDS < deadline )); do
        if response=$(curl -sS --fail --max-time 10 "$HEALTH_URL" 2>/dev/null) &&
            [[ "$(jq -r '.status // empty' <<<"$response")" == "UP" ]]; then
            return 0
        fi
        sleep 2
    done

    echo "Fineract health did not become UP within five minutes" >&2
    return 1
}

ensure_admin_password() {
    : "${FINERACT_ADMIN_PASSWORD:?FINERACT_ADMIN_PASSWORD must be set}"

    local status
    status=$(curl -sS -o /dev/null -w '%{http_code}' -u "mifos:${FINERACT_ADMIN_PASSWORD}" \
        -H "$TENANT_HEADER" "$BASE/offices")

    if [[ "$status" == "200" ]]; then
        AUTH_PASSWORD=$FINERACT_ADMIN_PASSWORD
        return 0
    fi

    if [[ "$status" != "401" ]]; then
        echo "Unable to authenticate mifos with the configured admin password (HTTP $status)" >&2
        return 1
    fi

    AUTH_PASSWORD=password
    api POST /authentication '{"username":"mifos","password":"password"}' >/dev/null
    api PUT /users/1 "{\"password\":$(jq -Rn --arg value "$FINERACT_ADMIN_PASSWORD" '$value'),\"repeatPassword\":$(jq -Rn --arg value "$FINERACT_ADMIN_PASSWORD" '$value')}" >/dev/null
    AUTH_PASSWORD=$FINERACT_ADMIN_PASSWORD
}

today_utc() {
    date -u +"%d %B %Y"
}
