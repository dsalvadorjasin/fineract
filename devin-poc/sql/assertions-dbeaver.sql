-- DBeaver-friendly variant of assertions.sql: plain SQL only (no psql
-- meta-commands), and the savings account is resolved by its seeded
-- external id instead of a psql variable. Run with Alt+X (execute script)
-- in a SQL editor bound to the fineract-poc (localhost) connection.

-- Savings account summary
SELECT id, account_no, external_id, status_enum, account_balance_derived
FROM m_savings_account
WHERE external_id = 'poc-savings-1';

-- Savings account transactions
SELECT t.id, t.transaction_type_enum, t.amount, t.transaction_date, t.is_reversed
FROM m_savings_account_transaction t
JOIN m_savings_account s ON s.id = t.savings_account_id
WHERE s.external_id = 'poc-savings-1'
ORDER BY t.id;

-- Journal entries for the savings account
SELECT j.id, j.entry_date, g.gl_code, g.name,
       CASE j.type_enum WHEN 2 THEN 'DEBIT' WHEN 1 THEN 'CREDIT' END AS entry_type,
       j.amount
FROM acc_gl_journal_entry j
JOIN acc_gl_account g ON g.id = j.account_id
JOIN m_savings_account_transaction t ON t.id = j.savings_transaction_id
JOIN m_savings_account s ON s.id = t.savings_account_id
WHERE s.external_id = 'poc-savings-1'
ORDER BY j.id;

-- Debit and credit totals
SELECT COALESCE(SUM(CASE WHEN j.type_enum = 2 THEN j.amount ELSE 0 END), 0) AS debit_total,
       COALESCE(SUM(CASE WHEN j.type_enum = 1 THEN j.amount ELSE 0 END), 0) AS credit_total,
       COALESCE(SUM(CASE WHEN j.type_enum = 2 THEN j.amount ELSE 0 END), 0)
         = COALESCE(SUM(CASE WHEN j.type_enum = 1 THEN j.amount ELSE 0 END), 0) AS balanced
FROM acc_gl_journal_entry j
JOIN m_savings_account_transaction t ON t.id = j.savings_transaction_id
JOIN m_savings_account s ON s.id = t.savings_account_id
WHERE s.external_id = 'poc-savings-1';

-- Pending maker-checker commands (status 2 = AWAITING_APPROVAL)
SELECT id, action_name, entity_name, status, made_on_date_utc
FROM m_portfolio_command_source
WHERE status = 2
ORDER BY id;

-- Relevant configuration
SELECT id, name, enabled, value
FROM c_configuration
WHERE name IN ('maker-checker', 'max-single-withdrawal-amount-savings')
   OR lower(name) LIKE '%same-maker-checker%'
ORDER BY id;
