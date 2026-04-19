import { expect, test } from '@playwright/test';
import {
  apiDelete,
  apiPost,
  createPrivateRoom,
  createPublicRoom,
  disposableUser,
  joinRoom,
  loginViaUi,
  logoutViaApi,
  registerViaApi,
  whoamiId,
} from './helpers/auth';

test.describe('chat rooms (§2.4)', () => {
  test('TC-ROOM-001 create public room lands on /rooms/:id with owner role', async ({
    browser,
  }) => {
    const u = disposableUser('rc1');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);

    const name = `e2e-rc1-${Date.now()}`;
    await page.goto('/rooms/new');
    await page.locator('input[formControlName="name"]').fill(name);
    await page.locator('textarea[formControlName="description"]').fill('a public room');
    await page.locator('input[formControlName="visibility"][value="public"]').check();
    await page.getByRole('button', { name: /^Create$/ }).click();

    await page.waitForURL(/\/rooms\/[0-9a-f-]+$/);
    const got = await ctx.request.get(`/api/rooms/mine`);
    const mine = (await got.json()) as Array<{ name: string; myRole: string }>;
    const match = mine.find((r) => r.name === name);
    expect(match?.myRole).toBe('owner');
    await ctx.close();
  });

  test('TC-ROOM-002 create private room is hidden from public catalog', async ({ browser }) => {
    const u = disposableUser('rc2');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPrivateRoom(boot.request, `e2e-priv-${Date.now()}`);

    const catalog = (await (await boot.request.get('/api/rooms')).json()) as Array<{
      id: string;
    }>;
    expect(catalog.map((r) => r.id)).not.toContain(room.id);

    await boot.close();
  });

  test('TC-ROOM-003 duplicate room name is rejected', async ({ browser }) => {
    const u = disposableUser('rc3');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);

    const name = `e2e-dupe-${Date.now()}`;
    const first = await createPublicRoom(boot.request, name);
    expect(first.id).toBeTruthy();
    const second = await apiPost(boot.request, '/api/rooms', {
      name,
      description: 'x',
      visibility: 'public',
    });
    expect(second.ok(), 'duplicate room name must be rejected').toBeFalsy();
    await boot.close();
  });

  test('TC-ROOM-004 public catalog shows name/description/member count', async ({ browser }) => {
    const u = disposableUser('rc4');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `e2e-cat-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);
    await page.goto('/rooms');
    const row = page.locator('.rooms-list li').filter({ hasText: room.name });
    await expect(row).toContainText('1 member');
    await expect(row).toContainText('e2e');

    await ctx.close();
  });

  test('TC-ROOM-005 public catalog search filters by name', async ({ browser }) => {
    const owner = disposableUser('rc5');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const stamp = Date.now().toString(36);
    const alpha = await createPublicRoom(boot.request, `alpha-${stamp}`);
    const beta = await createPublicRoom(boot.request, `beta-${stamp}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto('/rooms');
    await page.locator('input.search').fill(`alpha-${stamp}`);

    await expect(page.locator('.rooms-list li').filter({ hasText: alpha.name })).toBeVisible();
    await expect(page.locator('.rooms-list li').filter({ hasText: beta.name })).toHaveCount(0);

    await ctx.close();
  });

  test('TC-ROOM-006 join public room via Join button', async ({ browser }) => {
    const owner = disposableUser('rc6o');
    const joiner = disposableUser('rc6j');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-join-${Date.now()}`);
    await bootO.close();

    const bootJ = await browser.newContext();
    await registerViaApi(bootJ.request, joiner);
    await logoutViaApi(bootJ.request);
    await bootJ.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, joiner);
    await page.goto('/rooms');
    const row = page.locator('.rooms-list li').filter({ hasText: room.name });
    await row.getByRole('button', { name: /^Join$/ }).click();
    await page.waitForURL(new RegExp(`/rooms/${room.id}`));

    const mine = (await (await ctx.request.get('/api/rooms/mine')).json()) as Array<{
      id: string;
    }>;
    expect(mine.map((r) => r.id)).toContain(room.id);
    await ctx.close();
  });

  test('TC-ROOM-007 banned user cannot rejoin public room', async ({ browser }) => {
    const owner = disposableUser('rc7o');
    const joiner = disposableUser('rc7j');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-ban-${Date.now()}`);

    const bootJ = await browser.newContext();
    await registerViaApi(bootJ.request, joiner);
    const joinerId = await whoamiId(bootJ.request);
    await joinRoom(bootJ.request, room.id);

    const ban = await apiPost(bootO.request, `/api/rooms/${room.id}/bans`, {
      userId: joinerId,
      reason: 'e2e',
    });
    expect(ban.ok(), `ban: ${ban.status()} ${await ban.text()}`).toBeTruthy();

    const rejoin = await apiPost(bootJ.request, `/api/rooms/${room.id}/join`);
    expect(rejoin.ok(), 'banned user must not rejoin').toBeFalsy();

    await bootO.close();
    await bootJ.close();
  });

  test('TC-ROOM-008 member can leave the room', async ({ browser }) => {
    const owner = disposableUser('rc8o');
    const member = disposableUser('rc8m');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-leave-${Date.now()}`);
    await bootO.close();

    const bootM = await browser.newContext();
    await registerViaApi(bootM.request, member);
    await joinRoom(bootM.request, room.id);

    const leave = await apiDelete(bootM.request, `/api/rooms/${room.id}/members/me`);
    expect(leave.ok()).toBeTruthy();
    const mine = (await (await bootM.request.get('/api/rooms/mine')).json()) as Array<{
      id: string;
    }>;
    expect(mine.map((r) => r.id)).not.toContain(room.id);

    await bootM.close();
  });

  test('TC-ROOM-009 owner cannot leave their own room', async ({ browser }) => {
    const owner = disposableUser('rc9');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-ownerleave-${Date.now()}`);
    const leave = await apiDelete(boot.request, `/api/rooms/${room.id}/members/me`);
    expect(leave.ok(), 'owner leaving must be rejected').toBeFalsy();
    await boot.close();
  });

  test('TC-ROOM-010 owner deletes room — messages 404 for everyone', async ({ browser }) => {
    const owner = disposableUser('rc10o');
    const member = disposableUser('rc10m');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-del-${Date.now()}`);

    const bootM = await browser.newContext();
    await registerViaApi(bootM.request, member);
    await joinRoom(bootM.request, room.id);
    await apiPost(bootM.request, `/api/rooms/${room.id}/messages`, {
      text: 'hello before delete',
      replyToId: null,
      attachmentIds: [],
    });

    const del = await apiDelete(bootO.request, `/api/rooms/${room.id}`);
    expect(del.ok()).toBeTruthy();

    const lookup = await bootM.request.get(`/api/rooms/${room.id}`);
    expect(lookup.status()).toBeGreaterThanOrEqual(400);

    await bootO.close();
    await bootM.close();
  });

  test('TC-ROOM-011 private room refuses direct access to non-invitees', async ({ browser }) => {
    const owner = disposableUser('rc11o');
    const stranger = disposableUser('rc11s');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPrivateRoom(bootO.request, `e2e-priv-${Date.now()}`);
    await bootO.close();

    const bootS = await browser.newContext();
    await registerViaApi(bootS.request, stranger);
    const join = await apiPost(bootS.request, `/api/rooms/${room.id}/join`);
    expect(join.ok(), 'non-invitee must not join a private room').toBeFalsy();

    const look = await bootS.request.get(`/api/rooms/${room.id}`);
    expect(look.status()).toBeGreaterThanOrEqual(400);

    await bootS.close();
  });

  test('TC-ROOM-012..014 invitation send / accept / decline flow', async ({ browser }) => {
    const owner = disposableUser('rc12o');
    const invitee = disposableUser('rc12i');
    const declined = disposableUser('rc12d');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPrivateRoom(bootO.request, `e2e-invite-${Date.now()}`);

    const bootI = await browser.newContext();
    await registerViaApi(bootI.request, invitee);
    const bootD = await browser.newContext();
    await registerViaApi(bootD.request, declined);

    const invI = await apiPost(bootO.request, `/api/rooms/${room.id}/invitations`, {
      username: invitee.username,
    });
    expect(invI.ok(), `invite invitee: ${invI.status()} ${await invI.text()}`).toBeTruthy();
    const invD = await apiPost(bootO.request, `/api/rooms/${room.id}/invitations`, {
      username: declined.username,
    });
    expect(invD.ok()).toBeTruthy();

    const myI = (await (await bootI.request.get('/api/invitations')).json()) as Array<{
      id: string;
      roomId: string;
    }>;
    const rowI = myI.find((x) => x.roomId === room.id);
    expect(rowI).toBeTruthy();

    const acc = await apiPost(bootI.request, `/api/invitations/${rowI!.id}/accept`);
    expect(acc.ok()).toBeTruthy();
    const mine = (await (await bootI.request.get('/api/rooms/mine')).json()) as Array<{
      id: string;
    }>;
    expect(mine.map((r) => r.id)).toContain(room.id);

    const myD = (await (await bootD.request.get('/api/invitations')).json()) as Array<{
      id: string;
      roomId: string;
    }>;
    const rowD = myD.find((x) => x.roomId === room.id);
    expect(rowD).toBeTruthy();
    const dec = await apiPost(bootD.request, `/api/invitations/${rowD!.id}/decline`);
    expect(dec.ok()).toBeTruthy();
    const mineD = (await (await bootD.request.get('/api/rooms/mine')).json()) as Array<{
      id: string;
    }>;
    expect(mineD.map((r) => r.id)).not.toContain(room.id);

    await bootO.close();
    await bootI.close();
    await bootD.close();
  });

  test('TC-ROOM-025 edit room settings via Manage Room modal', async ({ browser }) => {
    const owner = disposableUser('rc25');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-edit-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await page.getByRole('button', { name: /^Manage room$/ }).click();

    await page.getByRole('button', { name: /^Settings$/ }).click();
    const newDesc = 'updated description';
    await page.locator('textarea[formControlName="description"]').fill(newDesc);
    await page.locator('input[formControlName="visibility"][value="private"]').check();
    await page.getByRole('button', { name: /^Save changes$/ }).click();

    await expect
      .poll(
        async () => {
          const r = (await (await ctx.request.get(`/api/rooms/${room.id}`)).json()) as {
            description: string;
            visibility: string;
          };
          return { description: r.description, visibility: r.visibility };
        },
        { timeout: 10_000 },
      )
      .toEqual({ description: newDesc, visibility: 'private' });
    await ctx.close();
  });

  test('TC-ROOM-026 renaming to existing room name is rejected', async ({ browser }) => {
    const u = disposableUser('rc26');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const stamp = Date.now().toString(36);
    const a = await createPublicRoom(boot.request, `alpha-${stamp}`);
    const b = await createPublicRoom(boot.request, `beta-${stamp}`);
    const clash = await boot.request.patch(`/api/rooms/${b.id}`, {
      data: { name: `alpha-${stamp}` },
      headers: { 'X-XSRF-TOKEN': await xsrf(boot.request) },
    });
    expect(clash.ok(), 'rename to existing name must fail').toBeFalsy();
    await boot.close();
  });
});

async function xsrf(request: import('@playwright/test').APIRequestContext): Promise<string> {
  await request.get('/api/auth/me').catch(() => undefined);
  const state = await request.storageState();
  return state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
}
