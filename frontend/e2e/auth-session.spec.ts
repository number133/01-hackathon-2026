import { expect, test } from '@playwright/test';
import {
  disposableUser,
  loginViaUi,
  registerViaApi,
  signOutViaUi,
} from './helpers/auth';

test.describe('authentication — session behavior (§2.1.3, §2.2.4)', () => {
  test('TC-AUTH-008 "Keep me signed in" persists across a new browser context', async ({
    browser,
  }) => {
    const u = disposableUser('remember');
    const ctxA = await browser.newContext();
    await registerViaApi(ctxA.request, u);
    await ctxA.close();

    const loginCtx = await browser.newContext();
    const loginPage = await loginCtx.newPage();
    await loginViaUi(loginPage, u, { rememberMe: true });
    const state = await loginCtx.storageState();
    await loginCtx.close();

    const freshCtx = await browser.newContext({ storageState: state });
    const freshPage = await freshCtx.newPage();
    await freshPage.goto('/');
    await expect(freshPage).not.toHaveURL(/\/login/);
    const me = await freshCtx.request.get('/api/auth/me');
    expect(me.ok()).toBeTruthy();
    await freshCtx.close();
  });

  test('TC-AUTH-009 sign-out invalidates only the current browser', async ({ browser }) => {
    const u = disposableUser('signout');
    const bootstrap = await browser.newContext();
    await registerViaApi(bootstrap.request, u);
    await bootstrap.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, u);

    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await loginViaUi(pageB, u);

    await signOutViaUi(pageA);
    await expect(pageA).toHaveURL(/\/login/);

    const meA = await ctxA.request.get('/api/auth/me');
    expect(meA.status(), 'context A must be unauthenticated').not.toBe(200);

    const meB = await ctxB.request.get('/api/auth/me');
    expect(meB.ok(), 'context B session must still be valid').toBeTruthy();
    await pageB.goto('/');
    await expect(pageB).not.toHaveURL(/\/login/);

    await ctxA.close();
    await ctxB.close();
  });
});
