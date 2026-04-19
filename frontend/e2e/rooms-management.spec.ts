import { expect, test } from '@playwright/test';
import {
  apiDelete,
  apiPost,
  createPublicRoom,
  disposableUser,
  joinRoom,
  loginViaUi,
  logoutViaApi,
  registerViaApi,
  sendRoomMessage,
  whoamiId,
} from './helpers/auth';

async function put(request: import('@playwright/test').APIRequestContext, url: string) {
  await request.get('/api/auth/me').catch(() => undefined);
  const state = await request.storageState();
  const xsrf = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  return request.fetch(url, { method: 'PUT', headers: { 'X-XSRF-TOKEN': xsrf } });
}

test.describe('room management (§2.4 admin/owner flows)', () => {
  test('TC-ROOM-015 admin deletes a message in the room', async ({ browser }) => {
    const owner = disposableUser('rm15o');
    const member = disposableUser('rm15m');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-delmsg-${Date.now()}`);

    const bootM = await browser.newContext();
    await registerViaApi(bootM.request, member);
    await joinRoom(bootM.request, room.id);
    const msg = await sendRoomMessage(bootM.request, room.id, 'message from member');

    const del = await apiDelete(bootO.request, `/api/messages/${msg.id}`);
    expect(del.ok(), `owner delete: ${del.status()}`).toBeTruthy();

    const history = (await (
      await bootO.request.get(`/api/rooms/${room.id}/messages`)
    ).json()) as { items: Array<{ id: string; deletedAt: string | null }> };
    const target = history.items.find((i) => i.id === msg.id);
    expect(target?.deletedAt).toBeTruthy();

    await bootO.close();
    await bootM.close();
  });

  test('TC-ROOM-016 removing a member drops their membership', async ({ browser }) => {
    // Requirements §2.4.8 say remove-by-admin should be treated as a ban. The
    // current implementation does NOT add a ban row on plain Remove (only
    // explicit Ban does — see RoomMembersController.remove vs .ban, and the
    // manage-room confirmation copy: "They can rejoin a public room…").
    // This test captures the actual behavior; the requirements-vs-impl gap
    // is documented in howto/tasks/test_scenarios.md.
    const owner = disposableUser('rm16o');
    const member = disposableUser('rm16m');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-remove-${Date.now()}`);

    const bootM = await browser.newContext();
    await registerViaApi(bootM.request, member);
    const memberId = await whoamiId(bootM.request);
    await joinRoom(bootM.request, room.id);

    const removed = await apiDelete(
      bootO.request,
      `/api/rooms/${room.id}/members/${memberId}`,
    );
    expect(removed.ok()).toBeTruthy();

    const members = (await (
      await bootO.request.get(`/api/rooms/${room.id}/members`)
    ).json()) as Array<{ userId: string }>;
    expect(members.map((m) => m.userId)).not.toContain(memberId);

    await bootO.close();
    await bootM.close();
  });

  test('TC-ROOM-017+018+019 explicit ban / list / unban flow', async ({ browser }) => {
    const owner = disposableUser('rm17o');
    const member = disposableUser('rm17m');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-banflow-${Date.now()}`);

    const bootM = await browser.newContext();
    await registerViaApi(bootM.request, member);
    const memberId = await whoamiId(bootM.request);
    await joinRoom(bootM.request, room.id);

    const ban = await apiPost(bootO.request, `/api/rooms/${room.id}/bans`, {
      userId: memberId,
      reason: 'e2e test ban',
    });
    expect(ban.ok(), `ban: ${ban.status()}`).toBeTruthy();

    const bans1 = (await (await bootO.request.get(`/api/rooms/${room.id}/bans`)).json()) as Array<{
      userId: string;
      reason: string | null;
      bannedByUsername: string;
    }>;
    const entry = bans1.find((b) => b.userId === memberId);
    expect(entry?.reason).toBe('e2e test ban');
    expect(entry?.bannedByUsername).toBe(owner.username);

    const unban = await apiDelete(bootO.request, `/api/rooms/${room.id}/bans/${memberId}`);
    expect(unban.ok()).toBeTruthy();

    const bans2 = (await (await bootO.request.get(`/api/rooms/${room.id}/bans`)).json()) as Array<{
      userId: string;
    }>;
    expect(bans2.map((b) => b.userId)).not.toContain(memberId);

    const rejoin = await apiPost(bootM.request, `/api/rooms/${room.id}/join`);
    expect(rejoin.ok(), 'after unban, user can rejoin').toBeTruthy();

    await bootO.close();
    await bootM.close();
  });

  test('TC-ROOM-020+021 promote / demote admin', async ({ browser }) => {
    const owner = disposableUser('rm20o');
    const candidate = disposableUser('rm20c');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-admin-${Date.now()}`);

    const bootC = await browser.newContext();
    await registerViaApi(bootC.request, candidate);
    const candidateId = await whoamiId(bootC.request);
    await joinRoom(bootC.request, room.id);

    const promote = await put(
      bootO.request,
      `/api/rooms/${room.id}/admins/${candidateId}`,
    );
    expect(promote.ok(), `promote: ${promote.status()}`).toBeTruthy();
    const members1 = (await (
      await bootO.request.get(`/api/rooms/${room.id}/members`)
    ).json()) as Array<{ userId: string; role: string }>;
    expect(members1.find((m) => m.userId === candidateId)?.role).toBe('admin');

    const demote = await apiDelete(
      bootO.request,
      `/api/rooms/${room.id}/admins/${candidateId}`,
    );
    expect(demote.ok()).toBeTruthy();
    const members2 = (await (
      await bootO.request.get(`/api/rooms/${room.id}/members`)
    ).json()) as Array<{ userId: string; role: string }>;
    expect(members2.find((m) => m.userId === candidateId)?.role).toBe('member');

    await bootO.close();
    await bootC.close();
  });

  test('TC-ROOM-022 owner cannot be demoted by anyone', async ({ browser }) => {
    const owner = disposableUser('rm22o');
    const admin = disposableUser('rm22a');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const ownerId = await whoamiId(bootO.request);
    const room = await createPublicRoom(bootO.request, `e2e-ownerlock-${Date.now()}`);

    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, admin);
    const adminId = await whoamiId(bootA.request);
    await joinRoom(bootA.request, room.id);
    const prom = await put(bootO.request, `/api/rooms/${room.id}/admins/${adminId}`);
    expect(prom.ok()).toBeTruthy();

    const selfDemote = await apiDelete(
      bootO.request,
      `/api/rooms/${room.id}/admins/${ownerId}`,
    );
    expect(selfDemote.ok(), 'owner demoting self must be rejected').toBeFalsy();

    const adminDemotesOwner = await apiDelete(
      bootA.request,
      `/api/rooms/${room.id}/admins/${ownerId}`,
    );
    expect(adminDemotesOwner.ok(), 'admin cannot demote owner').toBeFalsy();

    await bootO.close();
    await bootA.close();
  });

  test('TC-ROOM-023 non-owner does not see "Delete room" in Settings tab', async ({
    browser,
  }) => {
    const owner = disposableUser('rm23o');
    const admin = disposableUser('rm23a');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-nodel-${Date.now()}`);

    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, admin);
    const adminId = await whoamiId(bootA.request);
    await joinRoom(bootA.request, room.id);
    await put(bootO.request, `/api/rooms/${room.id}/admins/${adminId}`);
    await logoutViaApi(bootA.request);
    await bootO.close();
    await bootA.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, admin);
    await page.goto(`/rooms/${room.id}`);
    await page.getByRole('button', { name: /^Manage room$/ }).click();
    await page.getByRole('button', { name: /^Settings$/ }).click();

    await expect(page.getByRole('button', { name: /^Delete room$/ })).toHaveCount(0);
    await ctx.close();
  });

  test('TC-ROOM-024 banned user loses access to messages', async ({ browser }) => {
    const owner = disposableUser('rm24o');
    const victim = disposableUser('rm24v');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-lockout-${Date.now()}`);
    await sendRoomMessage(bootO.request, room.id, 'secret note');

    const bootV = await browser.newContext();
    await registerViaApi(bootV.request, victim);
    const victimId = await whoamiId(bootV.request);
    await joinRoom(bootV.request, room.id);

    const ok = await bootV.request.get(`/api/rooms/${room.id}/messages`);
    expect(ok.ok()).toBeTruthy();

    await apiPost(bootO.request, `/api/rooms/${room.id}/bans`, {
      userId: victimId,
    });

    const blocked = await bootV.request.get(`/api/rooms/${room.id}/messages`);
    expect(blocked.status(), 'banned user must lose message access').toBeGreaterThanOrEqual(400);

    await bootO.close();
    await bootV.close();
  });
});
