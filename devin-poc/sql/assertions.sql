\set ON_ERROR_STOP on

\echo '=== Savings account summary ==='
SELECT id, account_no, status_enum, account_balance_derived
FROM m_savings_account
WHERE id = :'savings_id';

\echo '=== Savings account transactions ==='
SELECT id, transaction_type_enum, amount, transaction_date, is_reversed
FROM m_savings_account_transaction
WHERE savings_account_id = :'savings_id'
ORDER BY id;

\echo '=== Journal entries for savings account ==='
SELECT j.id, j.entry_date, g.gl_code, g.name,
       CASE j.type_enum WHEN 2 THEN 'DEBIT' WHEN 1 THEN 'CREDIT' END AS entry_type,
       j.type_enum, j.amount
FROM acc_gl_journal_entry j
JOIN acc_gl_account g ON g.id = j.account_id
JOIN m_savings_account_transaction t ON t.id = j.savings_transaction_id
WHERE t.savings_account_id = :'savings_id'
ORDER BY j.id;

\echo '=== Debit and credit totals ==='
SELECT COALESCE(SUM(CASE WHEN j.type_enum = 2 THEN j.amount ELSE 0 END), 0) AS debit_total,
       COALESCE(SUM(CASE WHEN j.type_enum = 1 THEN j.amount ELSE 0 END), 0) AS credit_total,
       COALESCE(SUM(CASE WHEN j.type_enum = 2 THEN j.amount ELSE 0 END), 0)
         = COALESCE(SUM(CASE WHEN j.type_enum = 1 THEN j.amount ELSE 0 END), 0) AS balanced
FROM acc_gl_journal_entry j
JOIN m_savings_account_transaction t ON t.id = j.savings_transaction_id
WHERE t.savings_account_id = :'savings_id';

\echo '=== Pending maker-checker commands ==='
-- CommandProcessingResultType.AWAITING_APPROVAL is 2. This checkout stores
-- that enum in m_portfolio_command_source.status (older schemas called it
-- processing_result_enum).
SELECT id, action_name, entity_name, status AS processing_result_enum, made_on_date_utc
FROM m_portfolio_command_source
WHERE status = 2
ORDER BY id;

\echo '=== Relevant configuration ==='
SELECT id, name, enabled, value
FROM c_configuration
WHERE name IN ('maker-checker', 'max-single-withdrawal-amount-savings')
   OR lower(name) LIKE '%same-maker-checker%'
   OR lower(name) LIKE '%maker-checker-same-user%'
ORDER BY id;
