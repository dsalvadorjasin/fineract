import { expect, test } from '@playwright/test';
import { MAKER_USER, POC_MAKER_PASSWORD } from '../helpers/config';
import { login } from '../helpers/login';
import { savingsTransactionsPage } from '../helpers/ui';

test('maximum withdrawal is rejected by the UI', async ({ page }) => {
  test.skip(true, 'enabled by pilot change');
  await login(page, MAKER_USER, POC_MAKER_PASSWORD);
  await savingsTransactionsPage(page);
  await page.getByRole('button', { name: 'Savings Account Actions' }).click();
  await page.getByRole('menuitem', { name: 'Withdrawal', exact: true }).click();
  await page.locator('input[formcontrolname="transactionDate"]').fill(
    new Intl.DateTimeFormat('en-GB', {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
      timeZone: 'UTC',
    }).format(new Date()),
  );
  await page.locator('input.right-input').fill('99999');
  await page.locator('mat-select[formcontrolname="paymentTypeId"]').click();
  await page.getByRole('option').first().click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.getByText('exceeds the maximum permitted single withdrawal', { exact: false })).toBeVisible();
});
