package com.hackathon.chat.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.conversation.Conversation;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.dialog.DialogService;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomMember;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import com.hackathon.chat.ws.MessageBroadcaster;
import com.hackathon.chat.ws.WsEventEnvelope;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MessageServiceTest {

    private MessageRepository messageRepo;
    private RoomRepository roomRepo;
    private UserRepository userRepo;
    private RoomService roomService;
    private ConversationService conversationService;
    private DialogService dialogService;
    private com.hackathon.chat.attachment.AttachmentService attachmentService;
    private MessageBroadcaster broadcaster;
    private MessageService service;

    private UUID conversationId;
    private UUID roomId;
    private UUID userId;
    private Room room;

    @BeforeEach
    void setUp() throws Exception {
        messageRepo = Mockito.mock(MessageRepository.class);
        roomRepo = Mockito.mock(RoomRepository.class);
        userRepo = Mockito.mock(UserRepository.class);
        roomService = Mockito.mock(RoomService.class);
        conversationService = Mockito.mock(ConversationService.class);
        dialogService = Mockito.mock(DialogService.class);
        attachmentService = Mockito.mock(com.hackathon.chat.attachment.AttachmentService.class);
        broadcaster = Mockito.mock(MessageBroadcaster.class);
        service = new MessageService(messageRepo, roomRepo, userRepo,
                roomService, conversationService, dialogService, attachmentService,
                Mockito.mock(com.hackathon.chat.unread.UnreadService.class), broadcaster);
        when(attachmentService.refsByMessage(any())).thenReturn(java.util.Map.of());

        conversationId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        userId = UUID.randomUUID();
        room = new Room("r", "", "public", UUID.randomUUID());
        injectId(room, roomId);
        room.setConversationId(conversationId);
        when(roomService.requireRoom(roomId)).thenReturn(room);
        when(roomRepo.findByConversationId(conversationId)).thenReturn(Optional.of(room));
        when(userRepo.findAllById(any())).thenReturn(List.of());
        Conversation roomConversation = Mockito.mock(Conversation.class);
        when(roomConversation.getType()).thenReturn("room");
        when(conversationService.require(conversationId)).thenReturn(roomConversation);
    }

    @Test
    void postAssignsNextSeqAndBroadcasts() {
        when(roomService.requireMember(roomId, userId))
                .thenReturn(new RoomMember(roomId, userId, RoomMember.ROLE_MEMBER));
        when(conversationService.assignNextSeq(conversationId)).thenReturn(1L, 2L);
        when(messageRepo.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageView first = service.post(userId, roomId, new SendMessageRequest("hello", null, null));
        MessageView second = service.post(userId, roomId, new SendMessageRequest("hi again", null, null));

        assertThat(first.seq()).isEqualTo(1L);
        assertThat(second.seq()).isEqualTo(2L);
        verify(broadcaster, times(2)).publish(
                org.mockito.ArgumentMatchers.eq(WsEventEnvelope.EVENT_CREATED),
                org.mockito.ArgumentMatchers.eq(roomId),
                any(MessageView.class));
    }

    @Test
    void postRejectsNonMember() {
        when(roomService.requireMember(roomId, userId))
                .thenThrow(new ForbiddenException("not member"));

        assertThatThrownBy(() -> service.post(userId, roomId,
                new SendMessageRequest("hi", null, null)))
                .isInstanceOf(ForbiddenException.class);

        verify(messageRepo, never()).save(any());
        verify(broadcaster, never()).publish(any(), any(), any());
    }

    @Test
    void postRejectsOversizeUtf8Body() {
        when(roomService.requireMember(roomId, userId))
                .thenReturn(new RoomMember(roomId, userId, RoomMember.ROLE_MEMBER));
        // 4-byte emoji repeated to exceed the 3072-byte cap.
        String oversize = "\uD83D\uDE00".repeat(800);

        assertThatThrownBy(() -> service.post(userId, roomId,
                new SendMessageRequest(oversize, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(messageRepo, never()).save(any());
    }

    @Test
    void postRejectsReplyToInDifferentConversation() {
        when(roomService.requireMember(roomId, userId))
                .thenReturn(new RoomMember(roomId, userId, RoomMember.ROLE_MEMBER));
        UUID parentId = UUID.randomUUID();
        UUID otherConversation = UUID.randomUUID();
        Message parent = new Message(otherConversation, 1L, UUID.randomUUID(), "hi", null);
        when(messageRepo.findById(parentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.post(userId, roomId,
                new SendMessageRequest("reply", parentId, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editRejectsNonAuthor() {
        UUID mid = UUID.randomUUID();
        Message message = new Message(conversationId, 1L, UUID.randomUUID(), "x", null);
        when(messageRepo.findById(mid)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.edit(userId, mid, new EditMessageRequest("new")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void editRejectsDeleted() {
        UUID mid = UUID.randomUUID();
        Message message = new Message(conversationId, 1L, userId, "x", null);
        message.markDeleted();
        when(messageRepo.findById(mid)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.edit(userId, mid, new EditMessageRequest("new")))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void editSucceedsForAuthor() {
        UUID mid = UUID.randomUUID();
        Message message = new Message(conversationId, 1L, userId, "x", null);
        when(messageRepo.findById(mid)).thenReturn(Optional.of(message));

        MessageView view = service.edit(userId, mid, new EditMessageRequest("updated"));

        assertThat(message.getBody()).isEqualTo("updated");
        assertThat(message.getEditedAt()).isNotNull();
        verify(broadcaster).publish(
                org.mockito.ArgumentMatchers.eq(WsEventEnvelope.EVENT_EDITED),
                org.mockito.ArgumentMatchers.eq(roomId),
                any(MessageView.class));
    }

    @Test
    void deleteAllowedForAuthor() {
        UUID mid = UUID.randomUUID();
        Message message = new Message(conversationId, 1L, userId, "x", null);
        when(messageRepo.findById(mid)).thenReturn(Optional.of(message));

        service.delete(userId, mid);

        assertThat(message.isDeleted()).isTrue();
        verify(broadcaster).publish(
                org.mockito.ArgumentMatchers.eq(WsEventEnvelope.EVENT_DELETED),
                org.mockito.ArgumentMatchers.eq(roomId),
                any(MessageView.class));
    }

    @Test
    void deleteAllowedForRoomAdmin() {
        UUID mid = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Message message = new Message(conversationId, 1L, authorId, "x", null);
        when(messageRepo.findById(mid)).thenReturn(Optional.of(message));
        when(roomService.requireAdmin(roomId, adminId))
                .thenReturn(new RoomMember(roomId, adminId, RoomMember.ROLE_ADMIN));

        service.delete(adminId, mid);

        assertThat(message.isDeleted()).isTrue();
    }

    @Test
    void deleteIsIdempotentAndSilent() {
        UUID mid = UUID.randomUUID();
        Message message = new Message(conversationId, 1L, userId, "x", null);
        message.markDeleted();
        when(messageRepo.findById(mid)).thenReturn(Optional.of(message));

        service.delete(userId, mid);

        verify(broadcaster, never()).publish(any(), any(), any());
    }

    private static void injectId(Object target, UUID id) throws Exception {
        Field f = target.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(target, id);
    }
}
