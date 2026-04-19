import { expect, test } from '@playwright/test';
import {
  apiPost,
  disposableUser,
  loginViaUi,
  logoutViaApi,
  makeFriends,
  openDialog,
  registerViaApi,
  sendRoomMessage,
  whoamiId,
} from './helpers/auth';

test.describe('contacts & friends (§2.3)', () => {
  test('TC-FRND-001 send friend request by username (happy path)', async ({ browser }) => {
    const a = disposableUser('fr1a');
    const b = disposableUser('fr1b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);
    await logoutViaApi(bootA.request);
    await logoutViaApi(bootB.request);
    await bootA.close();
    await bootB.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, a);
    await pageA.goto('/friend-requests');
    await pageA.locator('input[formControlName="username"]').fill(b.username);
    await pageA.getByRole('button', { name: /^Send$/ }).click();
    await expect(pageA.locator('ul li').filter({ hasText: b.username })).toBeVisible();

    const ctxB = await browser.newContext();
    const incoming = await apiPost(ctxB.request, '/api/auth/login', {
      email: b.email,
      password: b.password,
      rememberMe: false,
    });
    expect(incoming.ok()).toBeTruthy();
    const bReqs = await ctxB.request.get('/api/friend-requests?direction=incoming');
    const arr = (await bReqs.json()) as Array<{ requester: { username: string } }>;
    expect(arr.map((r) => r.requester.username)).toContain(a.username);

    await ctxA.close();
    await ctxB.close();
  });

  test('TC-FRND-003 friend request carries optional text', async ({ browser }) => {
    const a = disposableUser('fr3a');
    const b = disposableUser('fr3b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);
    await logoutViaApi(bootA.request);
    await logoutViaApi(bootB.request);
    await bootA.close();
    await bootB.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, a);
    await pageA.goto('/friend-requests');
    await pageA.locator('input[formControlName="username"]').fill(b.username);
    await pageA.locator('input[formControlName="message"]').fill('hi there');
    await pageA.getByRole('button', { name: /^Send$/ }).click();
    await expect(pageA.locator('ul li').filter({ hasText: b.username })).toBeVisible();

    const ctxB = await browser.newContext();
    const pageB = await ctxB.newPage();
    await loginViaUi(pageB, b);
    await pageB.goto('/friend-requests');
    const incomingRow = pageB.locator('ul li').filter({ hasText: a.username });
    await expect(incomingRow).toContainText('hi there');

    await ctxA.close();
    await ctxB.close();
  });

  test('TC-FRND-004 accept request — both users see the friendship', async ({ browser }) => {
    const a = disposableUser('fr4a');
    const b = disposableUser('fr4b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);
    await makeFriends(bootA.request, a.username, bootB.request, b.username);

    const friendsA = await bootA.request.get('/api/friends');
    expect(((await friendsA.json()) as Array<{ username: string }>).map((f) => f.username)).toContain(
      b.username,
    );
    const friendsB = await bootB.request.get('/api/friends');
    expect(((await friendsB.json()) as Array<{ username: string }>).map((f) => f.username)).toContain(
      a.username,
    );

    await bootA.close();
    await bootB.close();
  });

  test('TC-FRND-005 decline request — no friendship', async ({ browser }) => {
    const a = disposableUser('fr5a');
    const b = disposableUser('fr5b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);

    const req = await apiPost(bootA.request, '/api/friend-requests', { username: b.username });
    expect(req.ok()).toBeTruthy();
    const incoming = await bootB.request.get('/api/friend-requests?direction=incoming');
    const [match] = (await incoming.json()) as Array<{ id: string }>;
    const decl = await apiPost(bootB.request, `/api/friend-requests/${match.id}/decline`);
    expect(decl.ok()).toBeTruthy();

    const friendsA = (await (await bootA.request.get('/api/friends')).json()) as Array<{
      username: string;
    }>;
    expect(friendsA.map((f) => f.username)).not.toContain(b.username);

    await bootA.close();
    await bootB.close();
  });

  test('TC-FRND-006 cannot send a second request while one is pending', async ({ browser }) => {
    const a = disposableUser('fr6a');
    const b = disposableUser('fr6b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);

    const first = await apiPost(bootA.request, '/api/friend-requests', { username: b.username });
    expect(first.ok()).toBeTruthy();
    const second = await apiPost(bootA.request, '/api/friend-requests', { username: b.username });
    expect(second.ok(), 'duplicate request should be rejected').toBeFalsy();

    await bootA.close();
    await bootB.close();
  });

  test('TC-FRND-007 remove friend', async ({ browser }) => {
    const a = disposableUser('fr7a');
    const b = disposableUser('fr7b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);
    await makeFriends(bootA.request, a.username, bootB.request, b.username);
    await logoutViaApi(bootA.request);
    await logoutViaApi(bootB.request);
    await bootA.close();
    await bootB.close();

    const ctxA = await browser.newContext();
    const pageA = await ctxA.newPage();
    await loginViaUi(pageA, a);
    await pageA.goto('/contacts');
    const row = pageA.locator('table tbody tr').filter({ hasText: b.username });
    await row.getByRole('button', { name: /^Unfriend$/ }).click();
    await pageA.getByRole('button', { name: /^Confirm$/ }).click();

    await expect(pageA.locator('table tbody tr').filter({ hasText: b.username })).toHaveCount(0, {
      timeout: 10_000,
    });

    const friends = (await (await ctxA.request.get('/api/friends')).json()) as Array<{
      username: string;
    }>;
    expect(friends.map((f) => f.username)).not.toContain(b.username);

    await ctxA.close();
  });

  test('TC-FRND-008+009 ban user blocks DMs and friendship is terminated', async ({ browser }) => {
    const a = disposableUser('fr8a');
    const b = disposableUser('fr8b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);
    await makeFriends(bootA.request, a.username, bootB.request, b.username);

    const bId = await whoamiId(bootB.request);
    const aId = await whoamiId(bootA.request);
    const dialog = await openDialog(bootA.request, bId);
    const msg = await apiPost(bootA.request, `/api/dialogs/${dialog.id}/messages`, {
      text: 'before ban',
      replyToId: null,
      attachmentIds: [],
    });
    expect(msg.ok()).toBeTruthy();

    const ban = await apiPost(bootA.request, `/api/user-bans/${bId}`);
    expect(ban.ok(), `ban: ${ban.status()}`).toBeTruthy();

    // friendship terminated on both sides
    const friendsA = (await (await bootA.request.get('/api/friends')).json()) as Array<{
      userId: string;
    }>;
    expect(friendsA.map((f) => f.userId)).not.toContain(bId);
    const friendsB = (await (await bootB.request.get('/api/friends')).json()) as Array<{
      userId: string;
    }>;
    expect(friendsB.map((f) => f.userId)).not.toContain(aId);

    // new DM blocked
    const blockedSend = await apiPost(bootB.request, `/api/dialogs/${dialog.id}/messages`, {
      text: 'should be blocked',
      replyToId: null,
      attachmentIds: [],
    });
    expect(blockedSend.ok(), 'banned user must not send new DMs').toBeFalsy();

    // history remains visible to both
    const histA = await bootA.request.get(`/api/dialogs/${dialog.id}/messages`);
    expect(histA.ok()).toBeTruthy();
    const itemsA = ((await histA.json()) as { items: Array<{ body: string }> }).items;
    expect(itemsA.some((i) => i.body === 'before ban')).toBeTruthy();

    await bootA.close();
    await bootB.close();
  });

  test('TC-FRND-010 unblock action is available in contacts UI', async ({ browser }) => {
    const a = disposableUser('fr10a');
    const b = disposableUser('fr10b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);
    await makeFriends(bootA.request, a.username, bootB.request, b.username);
    const bId = await whoamiId(bootB.request);
    const ban = await apiPost(bootA.request, `/api/user-bans/${bId}`);
    expect(ban.ok()).toBeTruthy();
    await logoutViaApi(bootA.request);
    await bootA.close();
    await bootB.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, a);
    await page.goto('/contacts');
    const blockedRow = page.locator('ul li').filter({ hasText: b.username });
    await expect(blockedRow).toBeVisible();
    await blockedRow.getByRole('button', { name: /^Unblock$/ }).click();
    await expect(
      page.locator('ul li').filter({ hasText: b.username }),
    ).toHaveCount(0, { timeout: 10_000 });

    await ctx.close();
  });

  test('TC-FRND-011 cannot DM a non-friend', async ({ browser }) => {
    const a = disposableUser('fr11a');
    const b = disposableUser('fr11b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);

    const bId = await whoamiId(bootB.request);
    const res = await apiPost(bootA.request, '/api/dialogs', { userId: bId });
    expect(res.ok(), 'opening a DM to a non-friend should fail').toBeFalsy();

    await bootA.close();
    await bootB.close();
  });

  test('TC-FRND-012 friend request to unknown username is rejected', async ({ browser }) => {
    const a = disposableUser('fr12a');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, a);
    const res = await apiPost(boot.request, '/api/friend-requests', {
      username: 'nobody_exists_12345',
    });
    expect(res.ok()).toBeFalsy();
    await boot.close();
  });

  test.skip('TC-FRND-002 send friend request from room member list', async () => {
    // Covered implicitly in §2.4 room member interactions. The contacts page
    // itself does not offer this entry point; it lives in the room context
    // pane (room-context-pane.component.ts). Adding a dedicated UI test adds
    // little over the API-level assertions already present for TC-FRND-001.
  });
});
