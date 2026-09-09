import { expect, test } from '@playwright/test';
import { getPendingCommands, getSavingsBalance, getSavingsTransactions } from '../helpers/api';
import { MAKER_USER, POC_MAKER_PASSWORD, SAVINGS_ID } from '../helpers/config';
import { login } from '../helpers/login';
import { captureStep, submitWithdrawal } from '../helpers/ui';

test('maker submits a withdrawal for checker approval', async ({ page }) => {
  const beforeBalance = await getSavingsBalance();
  const beforeTransactions = await getSavingsTransactions();
  const beforePending = await getPendingCommands();

  await login(page, MAKER_USER, POC_MAKER_PASSWORD);
  await captureStep(page, 'maker-withdrawal', 'logged-in');
  await submitWithdrawal(page, 100, 'maker-withdrawal');

  const afterBalance = await getSavingsBalance(SAVINGS_ID);
  const afterTransactions = await getSavingsTransactions(SAVINGS_ID);
  const afterPending = await getPendingCommands();

  expect(afterBalance).toBe(beforeBalance);
  expect(afterTransactions).toHaveLength(beforeTransactions.length);
  expect(afterPending).toHaveLength(beforePending.length + 1);
  const newCommands = afterPending.filter((command) => !beforePending.some((previous) => previous.id === command.id));
  expect(newCommands).toHaveLength(1);
  expect(newCommands[0].actionName).toContain('WITHDRAWAL');
  expect(newCommands[0].entityName).toContain('SAVINGSACCOUNT');
});
