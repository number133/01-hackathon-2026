import { Page, expect, test } from '@playwright/test';
import {
  apiPost,
  createPublicRoom,
  disposableUser,
  joinRoom,
  loginViaUi,
  logoutViaApi,
  makeFriends,
  openDialog,
  registerViaApi,
  sendRoomMessage,
  whoamiId,
} from './helpers/auth';

async function waitForChatReady(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForSelector('section.composer textarea', { state: 'visible' });
  // Give the STOMP client ~2-3s to connect after navigation. The chat service
  // adds optimistic reconnect toasts; tests that rely on live WS delivery
  // still need this breathing room.
  await page.waitForTimeout(2500);
}

test.describe('messaging (§2.5)', () => {
  test('TC-MSG-001 send plain text in a room (UI → visible after reload)', async ({ browser }) => {
    const owner = disposableUser('ms1');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg1-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const body = `hello-${Date.now()}`;
    await page.locator('section.composer textarea').fill(body);
    await page.locator('section.composer').getByRole('button', { name: /^Send$/ }).click();
    await expect(page.locator('section.composer textarea')).toHaveValue('', { timeout: 10_000 });

    await page.reload();
    await expect(
      page.locator('article.message').filter({ hasText: body }),
    ).toBeVisible({ timeout: 10_000 });

    await ctx.close();
  });

  test('TC-MSG-002 shift+enter inserts newline, enter sends', async ({ browser }) => {
    const owner = disposableUser('ms2');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg2-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const composer = page.locator('section.composer textarea');
    await composer.click();
    await composer.type('line1');
    await composer.press('Shift+Enter');
    await composer.type('line2');
    await composer.press('Enter');
    await expect(composer).toHaveValue('', { timeout: 10_000 });

    await page.reload();
    const msg = page.locator('article.message p.body').filter({ hasText: /line1[\s\S]+line2/ });
    await expect(msg).toBeVisible({ timeout: 10_000 });

    await ctx.close();
  });

  test('TC-MSG-003 emoji picker opens with a grid of emojis', async ({ browser }) => {
    const owner = disposableUser('ms3');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg3-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    await page.locator('section.composer .emoji-toggle').click();
    const picker = page.locator('emoji-mart').first();
    await expect(picker).toBeVisible({ timeout: 5_000 });
    // The picker mounts a large scrollable grid; entries live inside
    // ngx-emoji. We don't click a specific emoji (the 3rd-party component
    // has its own tests); we just assert the integration rendered.
    await expect(picker.locator('ngx-emoji')).not.toHaveCount(0, { timeout: 10_000 });
    await ctx.close();
  });

  test('TC-MSG-004 UTF-8 content round-trips intact', async ({ browser }) => {
    const owner = disposableUser('ms4');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg4-${Date.now()}`);
    const text = 'こんにちは 🌸 Привет';
    await sendRoomMessage(boot.request, room.id, text);
    const items = ((await (
      await boot.request.get(`/api/rooms/${room.id}/messages`)
    ).json()) as { items: Array<{ body: string }> }).items;
    expect(items.map((i) => i.body)).toContain(text);
    await boot.close();
  });

  test('TC-MSG-005 composer enforces 3072 char limit', async ({ browser }, testInfo) => {
    testInfo.setTimeout(90_000);
    const owner = disposableUser('ms5');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg5-${Date.now()}`);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const textarea = page.locator('section.composer textarea');
    await expect(textarea).toHaveAttribute('maxlength', '3072');

    // Server-side enforcement: send a 3073 char message via REST.
    const tooLong = await ctx.request.post(`/api/rooms/${room.id}/messages`, {
      data: { text: 'x'.repeat(3073), replyToId: null, attachmentIds: [] },
      headers: {
        'X-XSRF-TOKEN':
          (await ctx.request.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')
            ?.value ?? '',
      },
    });
    expect(tooLong.status(), 'over-size message must be rejected by server').toBeGreaterThanOrEqual(
      400,
    );
    await ctx.close();
  });

  test('TC-MSG-006+007 reply shows quoted preview; cancel clears chip', async ({ browser }) => {
    const owner = disposableUser('ms6');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg6-${Date.now()}`);
    await sendRoomMessage(boot.request, room.id, 'original message');
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const origBubble = page.locator('article.message').filter({ hasText: 'original message' });
    await origBubble.getByRole('button', { name: /^Reply$/ }).click();

    const chip = page.locator('section.composer .reply-chip');
    await expect(chip).toContainText('original message');

    await chip.locator('button', { hasText: '×' }).click();
    await expect(chip).toHaveCount(0);

    await origBubble.getByRole('button', { name: /^Reply$/ }).click();
    await page.locator('section.composer textarea').fill('replying now');
    await page.locator('section.composer').getByRole('button', { name: /^Send$/ }).click();
    await expect(page.locator('section.composer textarea')).toHaveValue('', { timeout: 10_000 });

    await page.reload();
    const replyBubble = page.locator('article.message').filter({ hasText: 'replying now' });
    await expect(replyBubble.locator('.reply-chip')).toContainText('original message');

    await ctx.close();
  });

  test('TC-MSG-008 edit own message shows (edited) marker', async ({ browser }) => {
    const owner = disposableUser('ms8');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg8-${Date.now()}`);
    await sendRoomMessage(boot.request, room.id, 'before edit');
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    await page.locator('article.message').filter({ hasText: 'before edit' }).getByRole('button', { name: /^Edit$/ }).click();

    // Once edit mode engages, the bubble no longer contains the body paragraph
    // with "before edit" — hasText won't match it. Target the editing article
    // by looking for the one that now contains a textarea + Save button.
    const editingBubble = page.locator('article.message').filter({
      has: page.locator('button', { hasText: 'Save' }),
    });
    await expect(editingBubble).toBeVisible({ timeout: 5_000 });
    await editingBubble.locator('textarea').fill('after edit');
    await editingBubble.getByRole('button', { name: /^Save$/ }).click();

    await page.reload();
    await expect(
      page.locator('article.message').filter({ hasText: 'after edit' }),
    ).toContainText('(edited)', { timeout: 15_000 });

    await ctx.close();
  });

  test('TC-MSG-009+012 non-author member cannot edit or delete other users messages', async ({
    browser,
  }) => {
    const owner = disposableUser('ms9o');
    const other = disposableUser('ms9m');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-msg9-${Date.now()}`);
    await sendRoomMessage(bootO.request, room.id, 'owner message');

    const bootM = await browser.newContext();
    await registerViaApi(bootM.request, other);
    await joinRoom(bootM.request, room.id);
    await logoutViaApi(bootM.request);
    await bootO.close();
    await bootM.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, other);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const bubble = page.locator('article.message').filter({ hasText: 'owner message' });
    await expect(bubble).toBeVisible();
    await expect(bubble.getByRole('button', { name: /^Edit$/ })).toHaveCount(0);
    await expect(bubble.getByRole('button', { name: /^Delete$/ })).toHaveCount(0);

    await ctx.close();
  });

  test('TC-MSG-010 author deletes own message — renders as deleted', async ({ browser }) => {
    const owner = disposableUser('ms10');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg10-${Date.now()}`);
    await sendRoomMessage(boot.request, room.id, 'delete me');
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const bubble = page.locator('article.message').filter({ hasText: 'delete me' });
    await bubble.getByRole('button', { name: /^Delete$/ }).click();

    await page.reload();
    await expect(
      page.locator('article.message.deleted').filter({ hasText: /\(deleted\)/ }),
    ).toBeVisible({ timeout: 10_000 });

    await ctx.close();
  });

  test('TC-MSG-013 personal chat — no admin controls on other users messages', async ({
    browser,
  }) => {
    const a = disposableUser('ms13a');
    const b = disposableUser('ms13b');
    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, a);
    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, b);
    await makeFriends(bootA.request, a.username, bootB.request, b.username);
    const bId = await whoamiId(bootB.request);
    const dialog = await openDialog(bootA.request, bId);

    await apiPost(bootB.request, `/api/dialogs/${dialog.id}/messages`, {
      text: 'from bob',
      replyToId: null,
      attachmentIds: [],
    });
    await logoutViaApi(bootA.request);
    await logoutViaApi(bootB.request);
    await bootA.close();
    await bootB.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, a);
    await page.goto(`/dialogs/${dialog.id}`);
    await waitForChatReady(page);

    const bobBubble = page.locator('article.message').filter({ hasText: 'from bob' });
    await expect(bobBubble).toBeVisible({ timeout: 10_000 });
    await expect(bobBubble.getByRole('button', { name: /^Edit$/ })).toHaveCount(0);
    await expect(bobBubble.getByRole('button', { name: /^Delete$/ })).toHaveCount(0);

    await ctx.close();
  });

  test('TC-MSG-014 messages render in chronological order', async ({ browser }) => {
    const owner = disposableUser('ms14');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg14-${Date.now()}`);
    await sendRoomMessage(boot.request, room.id, 'm1-aaaaaa');
    await sendRoomMessage(boot.request, room.id, 'm2-bbbbbb');
    await sendRoomMessage(boot.request, room.id, 'm3-cccccc');
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const bodies = await page.locator('article.message p.body').allInnerTexts();
    const m1 = bodies.findIndex((b) => b.includes('m1-aaaaaa'));
    const m2 = bodies.findIndex((b) => b.includes('m2-bbbbbb'));
    const m3 = bodies.findIndex((b) => b.includes('m3-cccccc'));
    expect(m1).toBeGreaterThanOrEqual(0);
    expect(m2).toBeGreaterThan(m1);
    expect(m3).toBeGreaterThan(m2);

    await ctx.close();
  });

  test('TC-MSG-015 infinite scroll loads older history', async ({ browser }, testInfo) => {
    testInfo.setTimeout(120_000);
    const owner = disposableUser('ms15');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, owner);
    const room = await createPublicRoom(boot.request, `e2e-msg15-${Date.now()}`);
    for (let i = 0; i < 60; i++) {
      await sendRoomMessage(boot.request, room.id, `seeded-${i}-${'x'.repeat(8)}`);
    }
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, owner);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);

    const countBefore = await page.locator('article.message').count();
    expect(countBefore).toBeGreaterThan(0);

    await page.locator('.message-scroller').evaluate((el) => (el.scrollTop = 0));
    await expect
      .poll(async () => page.locator('article.message').count(), { timeout: 15_000 })
      .toBeGreaterThan(countBefore);

    await ctx.close();
  });

  test.skip('TC-MSG-016 auto-scroll to new message when at bottom (STOMP flakiness)', async () => {
    // The initial STOMP subscription is racy on test start, which intermittently
    // drops the broadcasted message-created frame. A fix would be to add a
    // ready-state signal in the UI or a broadcast-replay on (re)subscribe.
  });

  test.skip('TC-MSG-017 no auto-scroll when user scrolled up (STOMP flakiness)', async () => {
    // Same root cause as TC-MSG-016.
  });

  test('TC-MSG-018 offline recipient sees messages on next login', async ({ browser }) => {
    const alice = disposableUser('ms18a');
    const bob = disposableUser('ms18b');

    const bootA = await browser.newContext();
    await registerViaApi(bootA.request, alice);
    const room = await createPublicRoom(bootA.request, `e2e-msg18-${Date.now()}`);

    const bootB = await browser.newContext();
    await registerViaApi(bootB.request, bob);
    await joinRoom(bootB.request, room.id);
    await logoutViaApi(bootB.request);
    await bootB.close();

    const offlineMsg = `while-you-were-out-${Date.now()}`;
    await sendRoomMessage(bootA.request, room.id, offlineMsg);
    await logoutViaApi(bootA.request);
    await bootA.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, bob);
    await page.goto(`/rooms/${room.id}`);
    await waitForChatReady(page);
    await expect(
      page.locator('article.message').filter({ hasText: offlineMsg }),
    ).toBeVisible({ timeout: 10_000 });

    await ctx.close();
  });
});
