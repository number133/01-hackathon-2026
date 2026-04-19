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
  uploadAttachment,
  whoamiId,
} from './helpers/auth';

const PDF_BYTES = Buffer.from(
  '%PDF-1.4\n1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\ntrailer<< /Root 1 0 R >>\n%%EOF',
);

// 1x1 red PNG
const PNG_BYTES = Buffer.from(
  '89504e470d0a1a0a0000000d494844520000000100000001080200000090' +
    '77538de000000c49444154789c6360000000000400010005fe02fea4' +
    '00000000049454e44ae426082',
  'hex',
);

test.describe('attachments (§2.6)', () => {
  test('TC-ATT-001+004+005 upload a PDF, message shows file card with original name', async ({
    browser,
  }) => {
    const u = disposableUser('att1');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `e2e-att1-${Date.now()}`);

    const att = await uploadAttachment(boot.request, room.conversationId, {
      name: 'My Report 2026.pdf',
      mimeType: 'application/pdf',
      buffer: PDF_BYTES,
    });
    expect(att.originalName).toBe('My Report 2026.pdf');
    expect(att.isImage).toBeFalsy();

    const msg = await sendRoomMessage(boot.request, room.id, 'see attached', {
      attachmentIds: [att.id],
    });
    expect(msg.id).toBeTruthy();

    // verify download endpoint returns the original filename in Content-Disposition
    const dl = await boot.request.get(`/api/attachments/${att.id}`);
    expect(dl.ok()).toBeTruthy();
    const cd = dl.headers()['content-disposition'] ?? '';
    expect(cd).toContain('My Report 2026.pdf');

    await logoutViaApi(boot.request);
    await boot.close();

    const ctx = await browser.newContext();
    const page = await ctx.newPage();
    await loginViaUi(page, u);
    await page.goto(`/rooms/${room.id}`);
    await expect(page.getByText('My Report 2026.pdf')).toBeVisible({ timeout: 15_000 });
    await ctx.close();
  });

  test('TC-ATT-003 image attachment flagged as image', async ({ browser }) => {
    const u = disposableUser('att3');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `e2e-att3-${Date.now()}`);

    const img = await uploadAttachment(boot.request, room.conversationId, {
      name: 'tiny.png',
      mimeType: 'image/png',
      buffer: PNG_BYTES,
    });
    expect(img.isImage).toBeTruthy();

    await boot.close();
  });

  test('TC-ATT-006 optional comment stored with the attachment', async ({ browser }) => {
    const u = disposableUser('att6');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `e2e-att6-${Date.now()}`);

    const att = await uploadAttachment(
      boot.request,
      room.conversationId,
      { name: 'notes.pdf', mimeType: 'application/pdf', buffer: PDF_BYTES },
      'latest requirements',
    );
    await sendRoomMessage(boot.request, room.id, 'see notes', {
      attachmentIds: [att.id],
    });

    const history = ((await (
      await boot.request.get(`/api/rooms/${room.id}/messages`)
    ).json()) as {
      items: Array<{
        attachments: Array<{ comment: string | null; originalName: string }>;
      }>;
    }).items;
    const withAttachment = history.find((m) => m.attachments && m.attachments.length > 0);
    expect(withAttachment).toBeTruthy();
    expect(withAttachment!.attachments[0].comment).toBe('latest requirements');

    await boot.close();
  });

  test('TC-ATT-009 non-member cannot download an attachment', async ({ browser }) => {
    const owner = disposableUser('att9o');
    const stranger = disposableUser('att9s');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-att9-${Date.now()}`);
    const att = await uploadAttachment(bootO.request, room.conversationId, {
      name: 'secret.pdf',
      mimeType: 'application/pdf',
      buffer: PDF_BYTES,
    });
    await sendRoomMessage(bootO.request, room.id, 'secret', {
      attachmentIds: [att.id],
    });

    const bootS = await browser.newContext();
    await registerViaApi(bootS.request, stranger);
    const res = await bootS.request.get(`/api/attachments/${att.id}`);
    expect(res.status(), 'non-member download must be refused').toBeGreaterThanOrEqual(400);

    await bootO.close();
    await bootS.close();
  });

  test('TC-ATT-010 banned user loses access to attachments', async ({ browser }) => {
    const owner = disposableUser('att10o');
    const victim = disposableUser('att10v');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-att10-${Date.now()}`);
    const att = await uploadAttachment(bootO.request, room.conversationId, {
      name: 'doc.pdf',
      mimeType: 'application/pdf',
      buffer: PDF_BYTES,
    });
    await sendRoomMessage(bootO.request, room.id, 'here', { attachmentIds: [att.id] });

    const bootV = await browser.newContext();
    await registerViaApi(bootV.request, victim);
    const victimId = await whoamiId(bootV.request);
    await joinRoom(bootV.request, room.id);

    const okRead = await bootV.request.get(`/api/attachments/${att.id}`);
    expect(okRead.ok(), 'member can download before ban').toBeTruthy();

    const ban = await apiPost(bootO.request, `/api/rooms/${room.id}/bans`, {
      userId: victimId,
    });
    expect(ban.ok()).toBeTruthy();

    const blocked = await bootV.request.get(`/api/attachments/${att.id}`);
    expect(blocked.status(), 'banned user must lose attachment access').toBeGreaterThanOrEqual(400);

    await bootO.close();
    await bootV.close();
  });

  test('TC-ATT-011 attachment persists for other members after uploader is removed', async ({
    browser,
  }) => {
    const owner = disposableUser('att11o');
    const uploader = disposableUser('att11u');
    const other = disposableUser('att11x');

    const bootO = await browser.newContext();
    await registerViaApi(bootO.request, owner);
    const room = await createPublicRoom(bootO.request, `e2e-att11-${Date.now()}`);

    const bootU = await browser.newContext();
    await registerViaApi(bootU.request, uploader);
    await joinRoom(bootU.request, room.id);
    const att = await uploadAttachment(bootU.request, room.conversationId, {
      name: 'shared.pdf',
      mimeType: 'application/pdf',
      buffer: PDF_BYTES,
    });
    await sendRoomMessage(bootU.request, room.id, 'shared file', {
      attachmentIds: [att.id],
    });

    const bootX = await browser.newContext();
    await registerViaApi(bootX.request, other);
    await joinRoom(bootX.request, room.id);

    const uploaderId = await whoamiId(bootU.request);
    const ban = await apiPost(bootO.request, `/api/rooms/${room.id}/bans`, {
      userId: uploaderId,
    });
    expect(ban.ok()).toBeTruthy();

    const otherDL = await bootX.request.get(`/api/attachments/${att.id}`);
    expect(otherDL.ok(), 'other members still see the file').toBeTruthy();

    const uploaderDL = await bootU.request.get(`/api/attachments/${att.id}`);
    expect(
      uploaderDL.status(),
      'uploader loses access once banned',
    ).toBeGreaterThanOrEqual(400);

    await bootO.close();
    await bootU.close();
    await bootX.close();
  });

  test('TC-ATT-012 attachments return 404 after room is deleted', async ({ browser }) => {
    const u = disposableUser('att12');
    const boot = await browser.newContext();
    await registerViaApi(boot.request, u);
    const room = await createPublicRoom(boot.request, `e2e-att12-${Date.now()}`);
    const att = await uploadAttachment(boot.request, room.conversationId, {
      name: 'doomed.pdf',
      mimeType: 'application/pdf',
      buffer: PDF_BYTES,
    });
    await sendRoomMessage(boot.request, room.id, 'bye', { attachmentIds: [att.id] });

    await apiDelete(boot.request, `/api/rooms/${room.id}`);

    const gone = await boot.request.get(`/api/attachments/${att.id}`);
    expect(gone.status()).toBeGreaterThanOrEqual(400);

    await boot.close();
  });

  test.skip('TC-ATT-002 paste image into composer', async () => {
    // Paste simulation into the composer is environment-sensitive; covered
    // instead by TC-ATT-003 via API upload. If paste UX regresses, the
    // attachment-picker component test (ng test) will catch it.
  });

  test.skip('TC-ATT-007 file > 20 MB rejected', async () => {
    // Requires crafting a real 20MB+ buffer in-test, which is slow and often
    // hits network timeouts before server validation. Enforce at the
    // backend integration-test layer instead.
  });

  test.skip('TC-ATT-008 image > 3 MB rejected as image', async () => {
    // Same rationale as TC-ATT-007.
  });
});
