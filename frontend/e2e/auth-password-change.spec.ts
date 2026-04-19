import { expect, test } from '@playwright/test';
import {
  apiPost,
  disposableUser,
  loginViaUi,
  registerViaApi,
} from './helpers/auth';

test.describe('authentication — change password (§2.1.4)', () => {
  test('TC-AUTH-013 change password while logged in', async ({ page, browser }) => {
    const u = disposableUser('pwchg');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    await boot.close();

    await loginViaUi(page, u);

    const newPassword = 'Rotated-Secret-42';
    await page.goto('/account/password');
    await page.locator('input[formControlName="currentPassword"]').fill(u.password);
    await page.locator('input[formControlName="newPassword"]').fill(newPassword);
    await page.locator('input[formControlName="confirmPassword"]').fill(newPassword);
    await page.getByRole('button', { name: /^Update password$/ }).click();

    await page.waitForURL((url) => !url.pathname.endsWith('/account/password'));

    const verify = await browser.newContext();
    const viaNew = await apiPost(verify.request, '/api/auth/login', {
      email: u.email,
      password: newPassword,
      rememberMe: false,
    });
    expect(viaNew.ok(), 'login with the new password should succeed').toBeTruthy();
    const verify2 = await browser.newContext();
    const viaOld = await apiPost(verify2.request, '/api/auth/login', {
      email: u.email,
      password: u.password,
      rememberMe: false,
    });
    expect(viaOld.status(), 'login with the old password should be rejected').not.toBe(200);
    await verify.close();
    await verify2.close();
  });

  test('TC-AUTH-014 change password rejects wrong current password', async ({ page, browser }) => {
    const u = disposableUser('pwchg-wrong');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    await boot.close();

    await loginViaUi(page, u);

    await page.goto('/account/password');
    await page.locator('input[formControlName="currentPassword"]').fill('WrongCurrent-9999');
    await page.locator('input[formControlName="newPassword"]').fill('BrandNewPass123!');
    await page.locator('input[formControlName="confirmPassword"]').fill('BrandNewPass123!');
    await page.getByRole('button', { name: /^Update password$/ }).click();

    await expect(page.locator('.error')).toBeVisible();
    await expect(page).toHaveURL(/\/account\/password/);

    const verify = await browser.newContext();
    const viaOld = await apiPost(verify.request, '/api/auth/login', {
      email: u.email,
      password: u.password,
      rememberMe: false,
    });
    expect(viaOld.ok(), 'original password must still work').toBeTruthy();
    await verify.close();
  });
});
