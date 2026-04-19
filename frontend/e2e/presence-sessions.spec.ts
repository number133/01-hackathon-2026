import { expect, test } from '@playwright/test';
import {
  disposableUser,
  loginViaUi,
  logoutViaApi,
  makeFriends,
  registerViaApi,
} from './helpers/auth';

test.describe('presence & sessions (§2.2)', () => {
  test('TC-PRES-001 logged-in friend appears online in contacts list', async ({ browser }) => {
    const alice = disposableUser('prA');
    const bob = disposableUser('prB');

    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, alice);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, bob);
    await makeFriends(bootA.request, alice.username, bootB.request, bob.username);
    await logoutViaApi(bootA.request);
    await logoutViaApi(bootB.request);
    await bootA.close();
    await bootB.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, alice);

    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await loginViaUi(pageB, bob);

    await pageB.goto('/contacts');
    const row = pageB.locator('table tbody tr').filter({ hasText: alice.username });
    await expect(row.locator('.presence-dot.online').first()).toBeVisible({ timeout: 15_000 });

    await ctxA.close();
    await ctxB.close();
  });

  test.skip('TC-PRES-004 closing all tabs flips presence to offline for friends', async ({
    browser,
  }, testInfo) => {
    // Backend offline detection can take > 90 s in dev profile after all tabs
    // close; too flaky for the default suite. Covered in nightly under
    // E2E_SLOW=1 with a longer window. See test_scenarios.md §9.
    testInfo.setTimeout(120_000);
    const alice = disposableUser('poA');
    const bob = disposableUser('poB');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, alice);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, bob);
    await makeFriends(boot.request, alice.username, bootB.request, bob.username);
    await logoutViaApi(boot.request);
    await logoutViaApi(bootB.request);
    await boot.close();
    await bootB.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, alice);

    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await loginViaUi(pageB, bob);

    await pageB.goto('/contacts');
    const aliceRow = pageB.locator('table tbody tr').filter({ hasText: alice.username });
    await expect(aliceRow.locator('.presence-dot.online').first()).toBeVisible({
      timeout: 15_000,
    });

    await ctxA.close();

    await expect(aliceRow.locator('.presence-dot.offline').first()).toBeVisible({
      timeout: 90_000,
    });

    await ctxB.close();
  });

  test.skip('TC-PRES-002 AFK after 60s of inactivity (wall-clock, E2E_SLOW)', async () => {
    // Requires a 60s+ wait on wall-clock with no override; gated under E2E_SLOW=1
    // and should be run only in nightly. See test_scenarios.md §2.
  });

  test.skip('TC-PRES-003 activity in any tab keeps user online (wall-clock)', async () => {
    // Same constraint as TC-PRES-002.
  });

  test('TC-PRES-007 sessions list shows the current + other browsers', async ({ browser }) => {
    const u = disposableUser('sesslist');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, u);
    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await loginViaUi(pageB, u);

    await pageA.goto('/sessions');
    const rows = pageA.locator('table tbody tr');
    await expect(rows).toHaveCount(2, { timeout: 10_000 });
    await expect(pageA.locator('tr.current .badge', { hasText: /this browser/i })).toBeVisible();

    await ctxA.close();
    await ctxB.close();
  });

  test('TC-PRES-008 revoking a remote session from sessions screen invalidates it', async ({
    browser,
  }) => {
    const u = disposableUser('revoke-remote');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, u);
    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await loginViaUi(pageB, u);

    await pageA.goto('/sessions');
    await expect(pageA.locator('table tbody tr')).toHaveCount(2, { timeout: 10_000 });

    const otherRow = pageA.locator('table tbody tr').filter({ hasNot: pageA.locator('.badge') });
    await otherRow.locator('button.danger', { hasText: /^Revoke$/ }).click();

    await expect(pageA.locator('table tbody tr')).toHaveCount(1, { timeout: 10_000 });

    const meA = await ctxA.request.get('/api/auth/me');
    expect(meA.ok(), 'context A still signed in').toBeTruthy();
    const meB = await ctxB.request.get('/api/auth/me');
    expect(meB.status(), 'context B must be unauthenticated').not.toBe(200);

    await ctxA.close();
    await ctxB.close();
  });

  test('TC-PRES-009 revoking the current session signs the user out', async ({ browser }) => {
    const u = disposableUser('revoke-self');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);

    await page.goto('/sessions');
    await expect(page.locator('table tbody tr')).toHaveCount(1, { timeout: 10_000 });
    await page
      .locator('tr.current button.danger', { hasText: /^Revoke$/ })
      .click();

    await page.waitForURL(/\/login/);
    const me = await ctx.request.get('/api/auth/me');
    expect(me.status(), 'current session must now be unauthenticated').not.toBe(200);
    await ctx.close();
  });
});
