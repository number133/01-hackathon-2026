import { expect, test } from '@playwright/test';
import { USERS, loginViaUi } from './helpers/auth';

test.describe('authentication — sign in (§2.1.3)', () => {
  test('TC-AUTH-005 alice signs in and leaves /login', async ({ page }) => {
    await loginViaUi(page, USERS.alice);
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('TC-AUTH-006 wrong password keeps the user on /login with an error', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[formControlName="email"]').fill(USERS.alice.email);
    await page.locator('input[formControlName="password"]').fill('not-the-password');
    await page.getByRole('button', { name: /^Sign in$/ }).click();
    await expect(page.locator('.error')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('TC-AUTH-007 unknown email is rejected', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[formControlName="email"]').fill('nobody-here@e2e.test');
    await page.locator('input[formControlName="password"]').fill('DemoPass123!');
    await page.getByRole('button', { name: /^Sign in$/ }).click();
    await expect(page.locator('.error')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });
});
