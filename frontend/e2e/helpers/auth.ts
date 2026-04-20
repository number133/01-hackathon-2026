import { APIRequestContext, Page, expect } from '@playwright/test';

async function csrfHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  await request.get('/api/auth/me').catch(() => undefined);
  const state = await request.storageState();
  const xsrf = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value;
  return xsrf ? { 'X-XSRF-TOKEN': xsrf } : {};
}

export async function apiPost(
  request: APIRequestContext,
  url: string,
  data?: unknown,
) {
  return request.post(url, { data, headers: await csrfHeaders(request) });
}

export async function apiDelete(
  request: APIRequestContext,
  url: string,
  data?: unknown,
) {
  return request.delete(url, { data, headers: await csrfHeaders(request) });
}

export type DemoUser = { email: string; password: string };
export type NewUser = { email: string; username: string; password: string };

export const USERS = {
  alice: { email: 'alice@demo.test', password: 'DemoPass123!' },
  bob: { email: 'bob@demo.test', password: 'DemoPass123!' },
  carol: { email: 'carol@demo.test', password: 'DemoPass123!' },
} as const satisfies Record<string, DemoUser>;

export const SEED_USERNAMES = {
  alice: 'alice',
  bob: 'bob',
  carol: 'carol',
} as const;

let __counter = 0;
export function disposableUser(prefix = 'disp'): NewUser {
  const stamp = Date.now().toString(36) + (__counter++).toString(36);
  const safePrefix = prefix.replace(/[^A-Za-z0-9_.-]/g, '');
  const username = `${safePrefix}_${stamp}`.slice(0, 40);
  return {
    email: `${safePrefix}-${stamp}@e2e.test`,
    username,
    password: 'DemoPass123!',
  };
}

export async function loginViaUi(
  page: Page,
  user: DemoUser,
  options: { rememberMe?: boolean } = {},
): Promise<void> {
  await page.goto('/login');
  await page.locator('input[formControlName="email"]').fill(user.email);
  await page.locator('input[formControlName="password"]').fill(user.password);
  if (options.rememberMe) {
    await page.locator('input[formControlName="rememberMe"]').check();
  }
  await page.getByRole('button', { name: /^Sign in$/ }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

export async function loginViaApi(request: APIRequestContext, user: DemoUser): Promise<void> {
  const res = await apiPost(request, '/api/auth/login', {
    email: user.email,
    password: user.password,
    rememberMe: false,
  });
  expect(res.ok(), `login failed: ${res.status()} ${await res.text()}`).toBeTruthy();
}

export async function registerViaApi(
  request: APIRequestContext,
  u: NewUser,
): Promise<NewUser> {
  const res = await apiPost(request, '/api/auth/register', {
    email: u.email,
    username: u.username,
    password: u.password,
  });
  expect(res.ok(), `register failed: ${res.status()} ${await res.text()}`).toBeTruthy();
  return u;
}

export async function registerViaUi(page: Page, u: NewUser): Promise<void> {
  await page.goto('/register');
  await page.locator('input[formControlName="email"]').fill(u.email);
  await page.locator('input[formControlName="username"]').fill(u.username);
  await page.locator('input[formControlName="password"]').fill(u.password);
  await page.locator('input[formControlName="confirmPassword"]').fill(u.password);
  await page.getByRole('button', { name: /^Create account$/ }).click();
}

export async function logoutViaApi(request: APIRequestContext): Promise<void> {
  await apiPost(request, '/api/auth/logout').catch(() => undefined);
}

export async function signOutViaUi(page: Page): Promise<void> {
  await page.locator('.profile-menu .profile-toggle').click();
  await page.locator('.profile-menu button.signout').click();
  await page.waitForURL(/\/login$|\/$|\/login\?/);
}

export async function getLatestResetToken(
  request: APIRequestContext,
  email: string,
): Promise<string> {
  const res = await request.get('/api/dev/password-reset-tokens');
  expect(
    res.ok(),
    `dev token endpoint: ${res.status()} (is backend running in dev profile?)`,
  ).toBeTruthy();
  const tokens = (await res.json()) as Array<{
    email: string;
    token: string;
    expiresAt: string;
  }>;
  const match = tokens.find((t) => t.email === email);
  expect(match, `no reset token found for ${email}; got ${tokens.length} entries`).toBeTruthy();
  return match!.token;
}

export async function deleteAccountViaApi(
  request: APIRequestContext,
  password: string,
): Promise<void> {
  const res = await apiDelete(request, '/api/account', { password });
  expect(res.ok(), `delete account failed: ${res.status()} ${await res.text()}`).toBeTruthy();
}

export type CreatedRoom = { id: string; conversationId: string; name: string };

export async function createPublicRoom(
  request: APIRequestContext,
  name: string,
): Promise<CreatedRoom> {
  const res = await apiPost(request, '/api/rooms', {
    name,
    description: 'e2e',
    visibility: 'public',
  });
  expect(res.ok(), `create room failed: ${res.status()} ${await res.text()}`).toBeTruthy();
  return (await res.json()) as CreatedRoom;
}

export async function createPrivateRoom(
  request: APIRequestContext,
  name: string,
): Promise<CreatedRoom> {
  const res = await apiPost(request, '/api/rooms', {
    name,
    description: 'e2e',
    visibility: 'private',
  });
  expect(res.ok(), `create private room failed: ${res.status()} ${await res.text()}`).toBeTruthy();
  return (await res.json()) as CreatedRoom;
}

export async function joinRoom(request: APIRequestContext, roomId: string): Promise<void> {
  const res = await apiPost(request, `/api/rooms/${roomId}/join`);
  expect(res.ok(), `join room ${roomId}: ${res.status()} ${await res.text()}`).toBeTruthy();
}

export async function whoamiId(request: APIRequestContext): Promise<string> {
  const res = await request.get('/api/auth/me');
  expect(res.ok(), `whoami: ${res.status()}`).toBeTruthy();
  const body = (await res.json()) as { id: string };
  return body.id;
}

export async function sendFriendRequest(
  request: APIRequestContext,
  username: string,
  message?: string,
): Promise<{ id: string }> {
  const res = await apiPost(request, '/api/friend-requests', { username, message });
  expect(
    res.ok(),
    `send friend request ${username}: ${res.status()} ${await res.text()}`,
  ).toBeTruthy();
  return (await res.json()) as { id: string };
}

export async function acceptFriendRequest(
  request: APIRequestContext,
  id: string,
): Promise<void> {
  const res = await apiPost(request, `/api/friend-requests/${id}/accept`);
  expect(res.ok(), `accept ${id}: ${res.status()}`).toBeTruthy();
}

export async function makeFriends(
  aCtxRequest: APIRequestContext,
  aUsername: string,
  bCtxRequest: APIRequestContext,
  bUsername: string,
): Promise<void> {
  const reqRes = await apiPost(aCtxRequest, '/api/friend-requests', {
    username: bUsername,
  });
  expect(reqRes.ok(), `A->B request: ${reqRes.status()} ${await reqRes.text()}`).toBeTruthy();
  const list = await bCtxRequest.get('/api/friend-requests?direction=incoming');
  expect(list.ok()).toBeTruthy();
  const incoming = (await list.json()) as Array<{
    id: string;
    requester: { username: string };
    status: string;
  }>;
  const match = incoming.find(
    (r) => r.requester.username === aUsername && r.status === 'pending',
  );
  expect(match, `B did not receive request from ${aUsername}`).toBeTruthy();
  const acc = await apiPost(bCtxRequest, `/api/friend-requests/${match!.id}/accept`);
  expect(acc.ok(), `accept: ${acc.status()}`).toBeTruthy();
}

export async function openDialog(
  request: APIRequestContext,
  otherUserId: string,
): Promise<{ id: string; conversationId: string }> {
  const res = await apiPost(request, '/api/dialogs', { userId: otherUserId });
  expect(res.ok(), `open dialog: ${res.status()} ${await res.text()}`).toBeTruthy();
  return (await res.json()) as { id: string; conversationId: string };
}

export async function sendRoomMessage(
  request: APIRequestContext,
  roomId: string,
  text: string,
  opts: { replyToId?: string; attachmentIds?: string[] } = {},
): Promise<{ id: string; seq: number }> {
  const body = {
    text,
    replyToId: opts.replyToId ?? null,
    attachmentIds: opts.attachmentIds ?? [],
  };
  // Room message rate limit is 20 burst + 1/sec refill. Tests that seed
  // many messages would otherwise fail once the bucket drains; back off
  // on 429 and retry.
  for (let attempt = 0; attempt < 40; attempt++) {
    const res = await apiPost(request, `/api/rooms/${roomId}/messages`, body);
    if (res.status() === 429) {
      await new Promise((resolve) => setTimeout(resolve, 1100));
      continue;
    }
    expect(res.ok(), `send message: ${res.status()} ${await res.text()}`).toBeTruthy();
    return (await res.json()) as { id: string; seq: number };
  }
  throw new Error('send message: exhausted retries against rate limiter');
}

export async function uploadAttachment(
  request: APIRequestContext,
  conversationId: string,
  file: { name: string; mimeType: string; buffer: Buffer },
  comment?: string,
): Promise<{ id: string; originalName: string; isImage: boolean }> {
  await request.get('/api/auth/me').catch(() => undefined);
  const state = await request.storageState();
  const xsrf = state.cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  const multipart: Record<string, unknown> = {
    conversationId,
    file: { name: file.name, mimeType: file.mimeType, buffer: file.buffer },
  };
  if (comment) multipart.comment = comment;
  const res = await request.post('/api/attachments', {
    multipart,
    headers: { 'X-XSRF-TOKEN': xsrf },
  });
  expect(res.ok(), `upload: ${res.status()} ${await res.text()}`).toBeTruthy();
  return (await res.json()) as { id: string; originalName: string; isImage: boolean };
}
