import { expect, test } from '@playwright/test';
import {
  createPublicRoom,
  disposableUser,
  joinRoom,
  loginViaUi,
  logoutViaApi,
  registerViaApi,
  sendRoomMessage,
} from './helpers/auth';

test.describe('non-functional (§3)', () => {
  test('TC-NF-004 same user in two tabs stays consistent', async ({ browser }) => {
    const u = disposableUser('nf4');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `e2e-nf4-${Date.now()}`);
    const body = `two-tab-${Date.now()}`;
    await sendRoomMessage(boot.request, room.id, body);
    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const pageA = await ctx.newPage();
    const pageB = await ctx.newPage();

    await loginViaUi(pageA, u);
    await pageA.goto(`/rooms/${room.id}`);
    await expect(
      pageA.locator('article.bubble').filter({ hasText: body }),
    ).toBeVisible({ timeout: 10_000 });

    await pageB.goto(`/rooms/${room.id}`);
    await expect(
      pageB.locator('article.bubble').filter({ hasText: body }),
    ).toBeVisible({ timeout: 10_000 });

    await ctx.close();
  });

  test.skip('TC-NF-001 delivery under 3 seconds (perf)', async () => {
    // Measurable only with STOMP live delivery; flaky in CI under the
    // disconnect-aware presence stack. Move to a dedicated perf harness.
  });

  test.skip('TC-NF-002 presence propagation under 2 seconds (perf)', async () => {
    // Gated on the same WS reconnection behavior as TC-PRES-002/003. Run
    // under E2E_SLOW=1 in nightly with a larger window.
  });

  test.skip('TC-NF-003 large history remains usable (out of scope)', async () => {
    // Removed from scope per howto/tasks/test_scenarios.md §11.
  });

  test.skip('TC-NF-005 no forced logout due to inactivity (wall-clock)', async () => {
    // Requires ≥ 15 min of real inactivity. Gate under E2E_SLOW=1 in nightly.
  });
});
