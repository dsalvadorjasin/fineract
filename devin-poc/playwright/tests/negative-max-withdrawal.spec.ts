import { expect, test } from '@playwright/test';
import { apiGet } from '../helpers/api';
import { FINERACT_ADMIN_PASSWORD, MAKER_USER, POC_MAKER_PASSWORD } from '../helpers/config';
import { login } from '../helpers/login';
import { captureStep, savingsTransactionsPage, todayDisplay } from '../helpers/ui';

test.beforeAll(async () => {
  const response = (await apiGet(
    '/configurations/name/max-single-withdrawal-amount-savings',
    'mifos',
    FINERACT_ADMIN_PASSWORD,
  )) as Record<string, any>;
  const configuration = response.globalConfiguration ?? response;
  expect(configuration.enabled, 'max-single-withdrawal-amount-savings must be enabled').toBe(true);
  expect(Number(configuration.value), 'max-single-withdrawal-amount-savings must be 500').toBe(500);
});

test('maximum withdrawal is rejected by the UI', async ({ page }) => {
  await login(page, MAKER_USER, POC_MAKER_PASSWORD);
  await savingsTransactionsPage(page);
  await page.getByRole('button', { name: 'Savings Account Actions' }).click();
  await page.getByRole('menuitem', { name: 'Withdrawal', exact: true }).click();
  await page.locator('input[formcontrolname="transactionDate"]').fill(todayDisplay());
  await page.locator('input.right-input').fill('600');
  await page.locator('mat-select[formcontrolname="paymentTypeId"]').click();
  await page.getByRole('option').first().click();
  await captureStep(page, 'negative-max-withdrawal', 'withdrawal-form');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByText('Confirm Transaction Details', { exact: true }).last()).toBeVisible();
  await captureStep(page, 'negative-max-withdrawal', 'withdrawal-confirm');
  await page.getByRole('button', { name: 'Submit', exact: true }).click();
  await expect(page.getByText('exceeds the maximum permitted single withdrawal', { exact: false })).toBeVisible();
  await page.waitForTimeout(1000);
  await captureStep(page, 'negative-max-withdrawal', 'error');
});
