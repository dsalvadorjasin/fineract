import { expect, Page } from '@playwright/test';

export async function dismissDialogs(page: Page): Promise<void> {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const dialogs = page.locator('mat-dialog-container');
    let closed = false;
    for (let index = 0; index < await dialogs.count(); index += 1) {
      const dialog = dialogs.nth(index);
      if (!(await dialog.isVisible().catch(() => false))) {
        continue;
      }
      const dismiss = dialog.getByRole('button', { name: /close|dismiss|cancel|not now|skip/i }).first();
      if (await dismiss.isVisible().catch(() => false)) {
        await dismiss.click();
      } else {
        await page.keyboard.press('Escape');
      }
      closed = true;
      break;
    }
    if (!closed) {
      return;
    }
  }
}

export async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/#/login');
  await page.evaluate(() => sessionStorage.clear());
  await page.reload();
  await dismissDialogs(page);
  await page.getByPlaceholder('Enter your username').fill(username);
  await page.getByPlaceholder('Enter your password').fill(password);
  await page.getByRole('button', { name: 'Login', exact: true }).click();
  await expect(page).not.toHaveURL(/#\/login/, { timeout: 30_000 });
  await dismissDialogs(page);
}
