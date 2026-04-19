import { expect, test } from '@playwright/test';
import {
  USERS,
  apiPost,
  createPublicRoom,
  disposableUser,
  loginViaApi,
  loginViaUi,
  registerViaApi,
} from './helpers/auth';

test.describe('authentication — account management (§2.1.2, §2.1.5)', () => {
  test('TC-AUTH-018 username is immutable (no editable field on /account)', async ({ page }) => {
    await loginViaUi(page, USERS.alice);
    await page.goto('/account');

    await expect(page.getByText('Username', { exact: true })).toBeVisible();
    const usernameInputs = page.locator(
      'input[formControlName="username"], input[name="username"]',
    );
    await expect(usernameInputs).toHaveCount(0);

    const shown = page
      .locator('dl.profile dd')
      .filter({ hasText: /^[A-Za-z0-9_.-]+$/ })
      .first();
    await expect(shown).toContainText('alice');
  });

  test('TC-AUTH-015..017 delete own account cascades owned rooms and strips memberships', async ({
    browser,
  }) => {
    const u = disposableUser('owner');

    const ownerCtx = await browser.newContext();
    await registerViaApi(ownerCtx.request, u);

    const ownedRoom = await createPublicRoom(ownerCtx.request, `e2e-owned-${Date.now()}`);

    const aliceCtx = await browser.newContext();
    await loginViaApi(aliceCtx.request, USERS.alice);
    const aliceOwnedRoom = await createPublicRoom(
      aliceCtx.request,
      `e2e-alice-${Date.now()}`,
    );

    const joinByOwner = await apiPost(ownerCtx.request, `/api/rooms/${aliceOwnedRoom.id}/join`);
    expect(joinByOwner.ok(), `owner join alice's room: ${joinByOwner.status()}`).toBeTruthy();
    const joinByAlice = await apiPost(aliceCtx.request, `/api/rooms/${ownedRoom.id}/join`);
    expect(joinByAlice.ok(), `alice join owner's room: ${joinByAlice.status()}`).toBeTruthy();

    const ownerPage = await ownerCtx.newPage();
    await loginViaUi(ownerPage, u);
    await ownerPage.goto('/account');
    await ownerPage.getByRole('button', { name: /^Delete account$/ }).click();
    await ownerPage
      .locator('input[formControlName="password"]')
      .fill(u.password);
    await ownerPage
      .getByRole('button', { name: /^Yes, delete my account$/ })
      .click();
    await ownerPage.waitForURL(/\/(login)?$/);

    // TC-AUTH-015: credentials no longer valid.
    const freshCtx = await browser.newContext();
    const relogin = await apiPost(freshCtx.request, '/api/auth/login', {
      email: u.email,
      password: u.password,
      rememberMe: false,
    });
    expect(relogin.status(), 'deleted account cannot log in').not.toBe(200);
    await freshCtx.close();

    // TC-AUTH-016: owner's room is gone; messages/attachments cascade-deleted.
    const ownedLookup = await aliceCtx.request.get(`/api/rooms/${ownedRoom.id}`);
    expect(
      ownedLookup.status(),
      'owned room should be gone',
    ).toBeGreaterThanOrEqual(400);

    // TC-AUTH-017: alice's own room persists; disposable is no longer a member.
    const aliceRoomLookup = await aliceCtx.request.get(`/api/rooms/${aliceOwnedRoom.id}`);
    expect(aliceRoomLookup.ok(), "alice's room must still exist").toBeTruthy();
    const membersRes = await aliceCtx.request.get(
      `/api/rooms/${aliceOwnedRoom.id}/members`,
    );
    expect(membersRes.ok()).toBeTruthy();
    const members = (await membersRes.json()) as Array<{ username: string }>;
    expect(
      members.map((m) => m.username),
      'deleted user should not be in member list',
    ).not.toContain(u.username);

    await ownerCtx.close();
    await aliceCtx.close();
  });
});
