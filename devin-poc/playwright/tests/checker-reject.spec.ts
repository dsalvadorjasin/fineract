import { expect, test } from '@playwright/test';
import { getPendingCommands, getSavingsBalance, getSavingsTransactions } from '../helpers/api';
import { CHECKER_USER, MAKER_USER, POC_CHECKER_PASSWORD, POC_MAKER_PASSWORD, SAVINGS_ID } from '../helpers/config';
import { login } from '../helpers/login';
import { submitWithdrawal, processPendingWithdrawal, captureStep } from '../helpers/ui';

test('checker rejects a withdrawal', async ({ page }) => {
  const existingPending = await getPendingCommands();
  if (existingPending.length > 0) {
    await login(page, CHECKER_USER, POC_CHECKER_PASSWORD);
    for (let index = 0; index < existingPending.length; index += 1) {
      await processPendingWithdrawal(page, 'Reject', 'checker-reject-cleanup');
    }
  }

  const beforeBalance = await getSavingsBalance(SAVINGS_ID);
  const beforeTransactions = await getSavingsTransactions(SAVINGS_ID);

  await login(page, MAKER_USER, POC_MAKER_PASSWORD);
  await captureStep(page, 'checker-reject', 'maker-logged-in');
  await submitWithdrawal(page, 50, 'checker-reject');

  await login(page, CHECKER_USER, POC_CHECKER_PASSWORD);
  await captureStep(page, 'checker-reject', 'checker-logged-in');
  await processPendingWithdrawal(page, 'Reject', 'checker-reject');

  const afterBalance = await getSavingsBalance(SAVINGS_ID);
  const afterTransactions = await getSavingsTransactions(SAVINGS_ID);
  const afterPending = await getPendingCommands();

  expect(afterBalance).toBe(beforeBalance);
  expect(afterTransactions).toHaveLength(beforeTransactions.length);
  expect(afterPending).toHaveLength(0);
});
