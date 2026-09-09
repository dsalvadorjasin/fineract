import fs from 'node:fs';
import path from 'node:path';
import { expect, Page } from '@playwright/test';
import { getPendingCommands } from './api';
import { CLIENT_ID, MAKER_USER, POC_MAKER_PASSWORD, SAVINGS_ID } from './config';
import { dismissDialogs, login } from './login';

const evidenceDir = path.resolve(process.cwd(), '../evidence/playwright');

export async function captureStep(page: Page, testName: string, step: string): Promise<void> {
  fs.mkdirSync(evidenceDir, { recursive: true });
  await page.screenshot({
    path: path.join(evidenceDir, `${testName}-${step}.png`),
    fullPage: true,
  });
}

export function todayDisplay(): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date());
}

export async function savingsTransactionsPage(page: Page): Promise<void> {
  await page.goto(`/#/clients/${CLIENT_ID}/savings-accounts/${SAVINGS_ID}/transactions`);
  await expect(page.getByText('Transactions', { exact: true }).last()).toBeVisible();
  await dismissDialogs(page);
}

export async function submitWithdrawal(page: Page, amount: number, testName: string): Promise<void> {
  await savingsTransactionsPage(page);
  await page.getByRole('button', { name: 'Savings Account Actions' }).click();
  await page.getByRole('menuitem', { name: 'Withdrawal', exact: true }).click();

  await page.locator('input[formcontrolname="transactionDate"]').fill(todayDisplay());
  await page.locator('input.right-input').fill(String(amount));
  const paymentType = page.locator('mat-select[formcontrolname="paymentTypeId"]');
  await paymentType.click();
  await page.getByRole('option').first().click();
  await captureStep(page, testName, 'withdrawal-form');

  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByText('Confirm Transaction Details', { exact: true }).last()).toBeVisible();
  await captureStep(page, testName, 'withdrawal-confirm');
  await page.getByRole('button', { name: 'Submit', exact: true }).click();
  await expect(page.getByText('Transaction Complete', { exact: true }).last()).toBeVisible({ timeout: 30_000 });
  await captureStep(page, testName, 'withdrawal-complete');
  await dismissDialogs(page);
}

export async function ensurePendingWithdrawal(page: Page, amount: number, testName: string): Promise<void> {
  if ((await getPendingCommands()).length > 0) {
    return;
  }
  await login(page, MAKER_USER, POC_MAKER_PASSWORD);
  await submitWithdrawal(page, amount, testName);
}

export async function processPendingWithdrawal(page: Page, action: 'Approve' | 'Reject', testName: string): Promise<void> {
  await page.goto('/#/checker-inbox-and-tasks/checker-inbox');
  await expect(page.getByRole('row').filter({ hasText: 'WITHDRAWAL' }).filter({ hasText: 'SAVINGSACCOUNT' })).toHaveCount(1);
  await captureStep(page, testName, 'pending-command');
  await page.getByRole('row').filter({ hasText: 'WITHDRAWAL' }).getByRole('checkbox', { name: /Select checker item/ }).check();
  await page.getByRole('button', { name: action, exact: true }).click();
  await expect(page.getByText(/Are you sure you want to (approve|reject) checker/)).toBeVisible();
  await captureStep(page, testName, action === 'Approve' ? 'approve-confirm' : 'reject-confirm');
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(
    page.getByText(/tasks processed: 1 succeeded, 0 failed|No checker inbox data available/).first(),
  ).toBeVisible({ timeout: 30_000 });
  await captureStep(page, testName, action === 'Approve' ? 'approved' : 'rejected');
}
