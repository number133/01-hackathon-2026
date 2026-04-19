import { Page, expect, test } from '@playwright/test';
import { USERS, createPublicRoom, loginViaUi } from './helpers/auth';

const composer = (page: Page) => page.locator('section.composer');
const composerInput = (page: Page) =>
  composer(page).getByPlaceholder(/Type a message/i);
const sendButton = (page: Page) =>
  composer(page).getByRole('button', { name: /^Send$/ });
const messageWith = (page: Page, text: string) =>
  page.locator('article.message').filter({ hasText: text });

test('alice sends a message and bob receives it live', async ({ browser }) => {
  const aliceCtx = await browser.newContext();
  const alicePage = await aliceCtx.newPage();
  await loginViaUi(alicePage, USERS.alice);

  const room = await createPublicRoom(aliceCtx.request, `e2e-room-${Date.now()}`);

  await alicePage.goto(`/rooms/${room.id}`);
  await expect(composerInput(alicePage)).toBeVisible();

  const bobCtx = await browser.newContext();
  const bobPage = await bobCtx.newPage();
  await loginViaUi(bobPage, USERS.bob);
  await bobPage.goto(`/rooms/${room.id}`);

  // Public rooms may still gate membership behind an explicit join — click it if present.
  const joinBtn = bobPage.getByRole('button', { name: /^(join|join room)$/i });
  if (await joinBtn.first().isVisible().catch(() => false)) {
    await joinBtn.first().click();
  }
  await expect(composerInput(bobPage)).toBeVisible();

  const body = `hello from alice ${Date.now()}`;
  await composerInput(alicePage).fill(body);
  await sendButton(alicePage).click();

  await expect(messageWith(alicePage, body)).toBeVisible();
  await expect(messageWith(bobPage, body)).toBeVisible({ timeout: 15_000 });

  await aliceCtx.close();
  await bobCtx.close();
});
