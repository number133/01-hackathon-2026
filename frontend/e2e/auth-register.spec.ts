import { expect, test } from '@playwright/test';
import {
  SEED_USERNAMES,
  USERS,
  disposableUser,
  registerViaUi,
} from './helpers/auth';

test.describe('authentication — registration (§2.1.1, §2.1.2)', () => {
  test('TC-AUTH-001 register new user (happy path)', async ({ page }) => {
    const u = disposableUser('reg');
    await registerViaUi(page, u);

    await page.waitForURL(
      (url) => !url.pathname.startsWith('/register') && !url.pathname.startsWith('/login'),
    );
    const me = await page.request.get('/api/auth/me');
    expect(me.ok(), `GET /api/auth/me after register: ${me.status()}`).toBeTruthy();
    const body = (await me.json()) as { email: string; username: string };
    expect(body.email).toBe(u.email);
    expect(body.username).toBe(u.username);
  });

  test('TC-AUTH-002 duplicate email rejected', async ({ page }) => {
    const u = { ...disposableUser('dupe-email'), email: USERS.alice.email };
    await registerViaUi(page, u);
    await expect(page.locator('.error')).toBeVisible();
    await expect(page).toHaveURL(/\/register/);
  });

  test('TC-AUTH-003 duplicate username rejected', async ({ page }) => {
    const u = { ...disposableUser('dupe-username'), username: SEED_USERNAMES.alice };
    await registerViaUi(page, u);
    await expect(page.locator('.error')).toBeVisible();
    await expect(page).toHaveURL(/\/register/);
  });

  test('TC-AUTH-004 password confirmation mismatch blocks submission', async ({ page }) => {
    const u = disposableUser('mismatch');
    await page.goto('/register');
    await page.locator('input[formControlName="email"]').fill(u.email);
    await page.locator('input[formControlName="username"]').fill(u.username);
    await page.locator('input[formControlName="password"]').fill(u.password);
    await page.locator('input[formControlName="confirmPassword"]').fill('DifferentPass999!');
    await page.locator('input[formControlName="confirmPassword"]').blur();

    await expect(page.getByText(/Passwords do not match/i)).toBeVisible();

    await page.getByRole('button', { name: /^Create account$/ }).click();
    await expect(page).toHaveURL(/\/register/);

    const me = await page.request.get('/api/auth/me');
    expect(me.status(), 'no session should have been established').not.toBe(200);
  });
});
