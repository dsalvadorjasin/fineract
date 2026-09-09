#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

OUT_DIR="$SCRIPT_DIR/out"
IDS_FILE="$OUT_DIR/ids.env"
mkdir -p "$OUT_DIR"

wait_for_health
ensure_admin_password

json_id() {
    jq -r '.resourceId // .clientId // .savingsId // .id // empty'
}

lookup_role_id() {
    local name=$1
    api GET /roles | jq -r --arg name "$name" '[.[] | select(.name == $name)][0].id // empty'
}

ensure_role() {
    local name=$1 description=$2
    local role_id response
    role_id=$(lookup_role_id "$name")
    if [[ -z "$role_id" ]]; then
        response=$(api POST /roles "$(jq -cn --arg name "$name" --arg description "$description" '{name:$name,description:$description}')")
        role_id=$(json_id <<<"$response")
    fi
    [[ -n "$role_id" && "$role_id" != "null" ]] || { echo "Could not resolve role $name" >&2; return 1; }
    printf '%s' "$role_id"
}

set_role_permissions() {
    local role_id=$1
    shift
    local permissions_json
    permissions_json=$(printf '%s\n' "$@" | jq -Rsc 'split("\n") | map(select(length > 0)) | map({key: ., value: true}) | from_entries')
    api PUT "/roles/${role_id}/permissions" "$(jq -cn --argjson permissions "$permissions_json" '{permissions:$permissions}')" >/dev/null
}

lookup_user_id() {
    local username=$1
    api GET /users | jq -r --arg username "$username" '[.[] | select(.username == $username)][0].id // empty'
}

create_or_update_user() {
    local username=$1 password=$2 role_id=$3 first_name=$4 last_name=$5 email=$6
    local user_id response auth
    user_id=$(lookup_user_id "$username")
    if [[ -z "$user_id" ]]; then
        response=$(api POST /users "$(jq -cn \
            --arg username "$username" --arg firstname "$first_name" --arg lastname "$last_name" \
            --arg email "$email" --arg password "$password" --arg repeatPassword "$password" \
            --argjson role "$role_id" \
            '{username:$username,firstname:$firstname,lastname:$lastname,email:$email,officeId:1,roles:[$role],password:$password,repeatPassword:$repeatPassword,sendPasswordToEmail:false,passwordNeverExpires:true}')")
        user_id=$(json_id <<<"$response")
    else
        api PUT "/users/${user_id}" "$(jq -cn \
            --arg firstname "$first_name" --arg lastname "$last_name" --arg email "$email" \
            --argjson role "$role_id" \
            '{firstname:$firstname,lastname:$lastname,email:$email,officeId:1,roles:[$role],sendPasswordToEmail:false,passwordNeverExpires:true}')" >/dev/null
    fi
    [[ -n "$user_id" && "$user_id" != "null" ]] || { echo "Could not resolve user $username" >&2; return 1; }

    if ! auth=$(api POST /authentication "$(jq -cn --arg username "$username" --arg password "$password" '{username:$username,password:$password}')"); then
        api POST "/users/${user_id}/pwd" "$(jq -cn --arg password "$password" --arg repeatPassword "$password" '{password:$password,repeatPassword:$repeatPassword}')" >/dev/null
        auth=$(api POST /authentication "$(jq -cn --arg username "$username" --arg password "$password" '{username:$username,password:$password}')")
    fi
    if [[ "$(jq -r 'if .shouldRenewPassword == true then "true" else "false" end' <<<"$auth")" == "true" ]]; then
        api POST "/users/${user_id}/pwd" "$(jq -cn --arg password "$password" --arg repeatPassword "$password" '{password:$password,repeatPassword:$repeatPassword}')" >/dev/null
        auth=$(api POST /authentication "$(jq -cn --arg username "$username" --arg password "$password" '{username:$username,password:$password}')")
    fi
    [[ "$(jq -r '.authenticated // false' <<<"$auth")" == "true" ]] || { echo "Authentication failed for $username" >&2; return 1; }
    [[ "$(jq -r 'if .shouldRenewPassword == false then "false" else "true" end' <<<"$auth")" == "false" ]] || {
        echo "Password renewal remains required for $username" >&2
        return 1
    }
    printf '%s' "$user_id"
}

lookup_gl_id() {
    local code=$1
    api GET /glaccounts | jq -r --arg code "$code" '(if type == "object" then (.pageItems // []) else . end) | [.[] | select(.glCode == $code)][0].id // empty'
}

ensure_gl_account() {
    local name=$1 type=$2 code=$3
    local account_id response
    account_id=$(lookup_gl_id "$code")
    if [[ -z "$account_id" ]]; then
        response=$(api POST /glaccounts "$(jq -cn \
            --arg name "$name" --arg code "$code" --argjson type "$type" \
            '{name:$name,glCode:$code,accountType:$type,type:$type,manualEntriesAllowed:true,usage:1,description:$name}')")
        account_id=$(json_id <<<"$response")
    fi
    [[ -n "$account_id" && "$account_id" != "null" ]] || { echo "Could not resolve GL account $code" >&2; return 1; }
    printf '%s' "$account_id"
}

lookup_product_id() {
    api GET /savingsproducts | jq -r '(if type == "object" then (.pageItems // []) else . end) | [.[] | select(.name == "POC Savings" or .shortName == "POCS")][0].id // empty'
}

ensure_product() {
    local cash_id=$1 control_id=$2 interest_expense_id=$3 fee_id=$4 interest_income_id=$5
    local product_id response template
    product_id=$(lookup_product_id)
    if [[ -z "$product_id" ]]; then
        template=$(api GET /savingsproducts/template)
        local monthly_compounding monthly_posting daily_balance days_365 cash_accounting
        monthly_compounding=$(jq -r '.interestCompoundingPeriodTypeOptions[] | select(.value == "Monthly") | .id' <<<"$template")
        monthly_posting=$(jq -r '.interestPostingPeriodTypeOptions[] | select(.value == "Monthly") | .id' <<<"$template")
        daily_balance=$(jq -r '.interestCalculationTypeOptions[] | select(.value == "Daily Balance") | .id' <<<"$template")
        days_365=$(jq -r '.interestCalculationDaysInYearTypeOptions[] | select(.value == "365 Days") | .id' <<<"$template")
        cash_accounting=$(jq -r '.accountingRuleOptions[] | select(.value == "CASH BASED") | .id' <<<"$template")
        response=$(api POST /savingsproducts "$(jq -cn \
            --argjson cash "$cash_id" --argjson control "$control_id" --argjson interest "$interest_expense_id" --argjson fee "$fee_id" \
            --argjson interestIncome "$interest_income_id" \
            --argjson compounding "$monthly_compounding" --argjson posting "$monthly_posting" --argjson calculation "$daily_balance" \
            --argjson days "$days_365" --argjson accounting "$cash_accounting" \
            '{name:"POC Savings",shortName:"POCS",description:"POC cash savings product",currencyCode:"USD",digitsAfterDecimal:2,inMultiplesOf:0,locale:"en",nominalAnnualInterestRate:5,interestCompoundingPeriodType:$compounding,interestPostingPeriodType:$posting,interestCalculationType:$calculation,interestCalculationDaysInYearType:$days,accountingRule:$accounting,withdrawalFeeForTransfers:false,enforceMinRequiredBalance:false,allowOverdraft:false,withHoldTax:false,savingsReferenceAccountId:$cash,savingsControlAccountId:$control,overdraftPortfolioControlId:$cash,interestOnSavingsAccountId:$interest,incomeFromInterestId:$interestIncome,incomeFromFeeAccountId:$fee,incomeFromPenaltyAccountId:$fee,writeOffAccountId:$interest,transfersInSuspenseAccountId:$control}')")
        product_id=$(json_id <<<"$response")
        [[ -n "$product_id" && "$product_id" != "null" ]] || { echo "Could not resolve POC Savings product" >&2; return 1; }
    fi
    printf '%s' "$product_id"
}

lookup_client_id() {
    local response
    if response=$(api GET /clients/external-id/poc-client-1 2>/dev/null); then
        jq -r '.id // .clientId // empty' <<<"$response"
    fi
}

ensure_client() {
    local client_id response
    client_id=$(lookup_client_id || true)
    if [[ -z "$client_id" ]]; then
        response=$(api POST /clients "$(jq -cn --arg date "$(today_utc)" \
            '{officeId:1,legalFormId:1,firstname:"POC",lastname:"Client",externalId:"poc-client-1",dateFormat:"dd MMMM yyyy",locale:"en",active:true,activationDate:$date}')")
        client_id=$(json_id <<<"$response")
    fi
    [[ -n "$client_id" && "$client_id" != "null" ]] || { echo "Could not resolve POC Client" >&2; return 1; }
    printf '%s' "$client_id"
}

lookup_savings_id() {
    local response
    if response=$(api GET /savingsaccounts/external-id/poc-savings-1 2>/dev/null); then
        jq -r '.id // .savingsId // empty' <<<"$response"
    fi
}

ensure_savings_account() {
    local client_id product_id
    local savings_id response account
    client_id=$1
    product_id=$2
    savings_id=$(lookup_savings_id || true)
    if [[ -z "$savings_id" ]]; then
        response=$(api POST /savingsaccounts "$(jq -cn --arg date "$(today_utc)" --argjson client "$client_id" --argjson product "$product_id" \
            '{clientId:$client,productId:$product,externalId:"poc-savings-1",submittedOnDate:$date,dateFormat:"dd MMMM yyyy",locale:"en"}')")
        savings_id=$(json_id <<<"$response")
    fi
    [[ -n "$savings_id" && "$savings_id" != "null" ]] || { echo "Could not resolve POC Savings account" >&2; return 1; }

    account=$(api GET "/savingsaccounts/${savings_id}")
    case "$(jq -r '.status.id // empty' <<<"$account")" in
        100) api POST "/savingsaccounts/${savings_id}?command=approve" \
            "$(jq -cn --arg date "$(today_utc)" '{approvedOnDate:$date,dateFormat:"dd MMMM yyyy",locale:"en"}')" >/dev/null ||
            return 1 ;;
    esac
    account=$(api GET "/savingsaccounts/${savings_id}")
    case "$(jq -r '.status.id // empty' <<<"$account")" in
        200) api POST "/savingsaccounts/${savings_id}?command=activate" \
            "$(jq -cn --arg date "$(today_utc)" '{activatedOnDate:$date,dateFormat:"dd MMMM yyyy",locale:"en"}')" >/dev/null ||
            return 1 ;;
    esac

    if [[ "$(api GET "/savingsaccounts/${savings_id}?associations=transactions" | jq '.transactions // [] | length')" == "0" ]]; then
        api POST "/savingsaccounts/${savings_id}/transactions?command=deposit" \
            "$(jq -cn --arg date "$(today_utc)" '{transactionAmount:10000,paymentTypeId:1,transactionDate:$date,dateFormat:"dd MMMM yyyy",locale:"en"}')" >/dev/null ||
            return 1
    fi
    printf '%s' "$savings_id"
}

configurations=$(api GET /configurations)
maker_checker_config_id=$(jq -r '.globalConfiguration | [.[] | select(.name == "maker-checker")][0].id // empty' <<<"$configurations")
[[ -n "$maker_checker_config_id" ]] || { echo "maker-checker configuration was not found" >&2; exit 1; }
api PUT "/configurations/${maker_checker_config_id}" '{"enabled":true}' >/dev/null
if same_configs=$(jq -r '.globalConfiguration[] | select((.name // "" | ascii_downcase | test("same-maker-checker|maker-checker-same-user"))) | [.id,.name] | @tsv' <<<"$configurations"); [[ -n "$same_configs" ]]; then
    while IFS=$'\t' read -r config_id config_name; do
        api PUT "/configurations/${config_id}" '{"enabled":false}' >/dev/null
        echo "Disabled same-maker-checker configuration: $config_name"
    done <<<"$same_configs"
else
    echo "No same-maker-checker configuration found."
fi

api PUT /permissions '{"permissions":{"WITHDRAWAL_SAVINGSACCOUNT":true,"CREATE_JOURNALENTRY":true}}' >/dev/null

maker_role_id=$(ensure_role "POC Maker" "POC maker role")
checker_role_id=$(ensure_role "POC Checker" "POC checker role")
set_role_permissions "$maker_role_id" ALL_FUNCTIONS_READ DEPOSIT_SAVINGSACCOUNT WITHDRAWAL_SAVINGSACCOUNT
set_role_permissions "$checker_role_id" ALL_FUNCTIONS_READ CHECKER_SUPER_USER

maker_user_id=$(create_or_update_user poc_maker "$POC_MAKER_PASSWORD" "$maker_role_id" "POC" "Maker" "poc-maker@example.invalid")
checker_user_id=$(create_or_update_user poc_checker "$POC_CHECKER_PASSWORD" "$checker_role_id" "POC" "Checker" "poc-checker@example.invalid")

template=$(api GET /glaccounts/template)
asset_type=$(jq -r '.accountTypeOptions[] | select(.id == 1) | .id' <<<"$template")
liability_type=$(jq -r '.accountTypeOptions[] | select(.id == 2) | .id' <<<"$template")
income_type=$(jq -r '.accountTypeOptions[] | select(.id == 4) | .id' <<<"$template")
expense_type=$(jq -r '.accountTypeOptions[] | select(.id == 5) | .id' <<<"$template")
gl_cash_id=$(ensure_gl_account "POC Cash" "$asset_type" POC-1001)
gl_savings_control_id=$(ensure_gl_account "POC Savings Control" "$liability_type" POC-2001)
gl_interest_income_id=$(ensure_gl_account "POC Interest Income" "$income_type" POC-4001)
gl_fee_income_id=$(ensure_gl_account "POC Fee Income" "$income_type" POC-4002)
gl_interest_expense_id=$(ensure_gl_account "POC Interest Expense" "$expense_type" POC-5001)

product_id=$(ensure_product "$gl_cash_id" "$gl_savings_control_id" "$gl_interest_expense_id" "$gl_fee_income_id" "$gl_interest_income_id")
office_id=$(api GET /offices | jq -r '.[0].id')
client_id=$(ensure_client)
savings_id=$(ensure_savings_account "$client_id" "$product_id")
savings_account_no=$(api GET "/savingsaccounts/${savings_id}" | jq -r '.accountNo')

cat >"$IDS_FILE" <<EOF
OFFICE_ID=$office_id
CLIENT_ID=$client_id
SAVINGS_ID=$savings_id
SAVINGS_ACCOUNT_NO=$savings_account_no
PRODUCT_ID=$product_id
MAKER_USER=poc_maker
MAKER_USER_ID=$maker_user_id
CHECKER_USER=poc_checker
CHECKER_USER_ID=$checker_user_id
MAKER_ROLE_ID=$maker_role_id
CHECKER_ROLE_ID=$checker_role_id
GL_CASH_ID=$gl_cash_id
GL_SAVINGS_CONTROL_ID=$gl_savings_control_id
GL_INTEREST_INCOME_ID=$gl_interest_income_id
GL_FEE_INCOME_ID=$gl_fee_income_id
GL_INTEREST_EXPENSE_ID=$gl_interest_expense_id
MAKER_CHECKER_CONFIG_ID=$maker_checker_config_id
EOF

echo "Seed complete: client ${client_id}, savings account ${savings_account_no}, product ${product_id}."
