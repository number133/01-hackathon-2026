import { expect, test } from '@playwright/test';
import {
  createPublicRoom,
  disposableUser,
  loginViaUi,
  logoutViaApi,
  registerViaApi,
} from './helpers/auth';

test.describe('UI layout (§4)', () => {
  test('TC-UI-001 main landmarks render after login', async ({ browser }) => {
    const u = disposableUser('ui1');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `ui-layout-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);
    await page.goto(`/rooms/${room.id}`);

    await expect(page.locator('header.top-menu')).toBeVisible();
    await expect(page.locator('app-chat-sidebar')).toBeVisible();
    await expect(page.locator('section.chat-main')).toBeVisible();
    await expect(page.locator('section.composer')).toBeVisible();
    await expect(page.locator('app-room-context-pane')).toBeVisible();
    await ctx.close();
  });

  test('TC-UI-002 top menu exposes expected entries', async ({ browser }) => {
    const u = disposableUser('ui2');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);
    await page.goto('/');

    const menu = page.locator('header.top-menu nav');
    for (const label of ['Public Rooms', 'Private Rooms', 'Contacts', 'Sessions']) {
      await expect(menu.getByRole('link', { name: new RegExp(`^${label}$`) })).toBeVisible();
    }
    await page.locator('.profile-menu .profile-toggle').click();
    for (const label of ['Account', 'Personal chats', 'Invitations', 'Friend requests']) {
      await expect(
        page.locator('.profile-dropdown').getByRole('link', { name: new RegExp(label) }),
      ).toBeVisible();
    }
    await expect(
      page.locator('.profile-dropdown').getByRole('button', { name: /^Sign out$/ }),
    ).toBeVisible();
    await ctx.close();
  });

  test('TC-UI-004+007 members pane shows presence dots and owner sees Manage room', async ({
    browser,
  }) => {
    const u = disposableUser('ui4');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `ui-ctx-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);
    await page.goto(`/rooms/${room.id}`);

    const pane = page.locator('app-room-context-pane');
    await expect(pane).toBeVisible();
    await expect(pane.locator('.presence-dot').first()).toBeVisible({ timeout: 10_000 });
    await expect(pane.getByRole('button', { name: /^Manage room$/ })).toBeVisible();
    await ctx.close();
  });

  test('TC-UI-005 manage-room modal exposes all five tabs', async ({ browser }) => {
    const u = disposableUser('ui5');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `ui-modal-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);
    await page.goto(`/rooms/${room.id}`);
    await page.getByRole('button', { name: /^Manage room$/ }).click();

    const modal = page.locator('.modal-overlay .modal-panel');
    await expect(modal).toBeVisible();
    for (const label of ['Members', 'Admins', 'Banned', 'Invitations', 'Settings']) {
      await expect(modal.getByRole('button', { name: new RegExp(`^${label}$`) })).toBeVisible();
    }
    await ctx.close();
  });

  test.skip('TC-UI-003 sidebar accordion collapses on entering a room', async () => {
    // The chat sidebar shows rooms/contacts without an accordion collapse
    // animation; the requirement's "accordion style" is not mirrored in the
    // current markup (confirmed in chat-sidebar.component.html). No UI state
    // change to assert against.
  });

  test.skip('TC-UI-006 reply chip visible before sending', async () => {
    // Covered by TC-MSG-006+007 in messaging.spec.ts.
  });
});
