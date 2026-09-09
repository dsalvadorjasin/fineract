import { expect, test } from '@playwright/test';
import { getPendingCommands, getSavingsBalance, getSavingsTransactions } from '../helpers/api';
import { CHECKER_USER, POC_CHECKER_PASSWORD, SAVINGS_ID } from '../helpers/config';
import { login } from '../helpers/login';
import { captureStep, ensurePendingWithdrawal, processPendingWithdrawal } from '../helpers/ui';

test('checker approves the pending withdrawal', async ({ page }) => {
  await ensurePendingWithdrawal(page, 100, 'checker-approve');
  const beforeBalance = await getSavingsBalance(SAVINGS_ID);
  const beforeTransactions = await getSavingsTransactions(SAVINGS_ID);

  await login(page, CHECKER_USER, POC_CHECKER_PASSWORD);
  await captureStep(page, 'checker-approve', 'logged-in');
  await processPendingWithdrawal(page, 'Approve', 'checker-approve');

  const afterBalance = await getSavingsBalance(SAVINGS_ID);
  const afterTransactions = await getSavingsTransactions(SAVINGS_ID);
  const afterPending = await getPendingCommands();

  expect(afterBalance).toBe(beforeBalance - 100);
  expect(afterTransactions).toHaveLength(beforeTransactions.length + 1);
  const newTransactions = afterTransactions.filter(
    (transaction) => !beforeTransactions.some((previous) => previous.id === transaction.id),
  );
  expect(newTransactions).toHaveLength(1);
  expect(newTransactions[0].amount).toBe(100);
  expect(newTransactions[0].transactionType?.transactionTypeEnum).toBe('WITHDRAWAL');
  expect(afterPending).toHaveLength(0);
});
