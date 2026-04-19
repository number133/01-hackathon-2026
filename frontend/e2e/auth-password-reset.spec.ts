import { expect, test } from '@playwright/test';
import {
  apiPost,
  disposableUser,
  getLatestResetToken,
  loginViaUi,
  registerViaApi,
} from './helpers/auth';

test.describe('authentication — password reset (§2.1.4)', () => {
  test('TC-AUTH-010 request password reset link', async ({ page, request }) => {
    const u = disposableUser('pwreq');
    await registerViaApi(request, u);

    await page.goto('/forgot-password');
    await page.locator('input[formControlName="email"]').fill(u.email);
    await page.getByRole('button', { name: /^Send reset link$/ }).click();

    await expect(page.getByText(/If an account exists/i)).toBeVisible();

    const token = await getLatestResetToken(request, u.email);
    expect(token.length).toBeGreaterThan(0);
  });

  test('TC-AUTH-011 complete password reset with valid token', async ({ page, request }) => {
    const u = disposableUser('pwconfirm');
    await registerViaApi(request, u);

    const reqRes = await apiPost(request, '/api/auth/password-reset/request', {
      email: u.email,
    });
    expect(reqRes.ok()).toBeTruthy();
    const token = await getLatestResetToken(request, u.email);

    const newPassword = 'Brand-New-Pass-99';
    await page.goto(`/reset-password?token=${encodeURIComponent(token)}`);
    await expect(page.locator('input[formControlName="token"]')).toHaveValue(token);
    await page.locator('input[formControlName="newPassword"]').fill(newPassword);
    await page.locator('input[formControlName="confirmPassword"]').fill(newPassword);
    await page.getByRole('button', { name: /^Set new password$/ }).click();

    await expect(page.getByText(/Password updated/i)).toBeVisible();

    const oldLogin = await apiPost(request, '/api/auth/login', {
      email: u.email,
      password: u.password,
      rememberMe: false,
    });
    expect(oldLogin.status(), 'old password must now be rejected').not.toBe(200);

    await loginViaUi(page, { email: u.email, password: newPassword });
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('TC-AUTH-012 invalid token is rejected', async ({ page }) => {
    await page.goto('/reset-password?token=this-token-is-definitely-not-real');
    await page.locator('input[formControlName="newPassword"]').fill('SomeNewPass123!');
    await page.locator('input[formControlName="confirmPassword"]').fill('SomeNewPass123!');
    await page.getByRole('button', { name: /^Set new password$/ }).click();

    await expect(page.locator('.error')).toBeVisible();
    await expect(page.getByText(/Password updated/i)).toHaveCount(0);
  });
});
