package com.hackathon.chat.message;

import com.hackathon.chat.attachment.AttachmentRef;
import com.hackathon.chat.attachment.AttachmentService;
import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.conversation.Conversation;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.dialog.Dialog;
import com.hackathon.chat.dialog.DialogService;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.unread.UnreadService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import com.hackathon.chat.ws.MessageBroadcaster;
import com.hackathon.chat.ws.WsEventEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MessageService {

    public static final int BODY_MAX_BYTES = 3072;
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_LENGTH = 120;

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomService roomService;
    private final ConversationService conversationService;
    private final DialogService dialogService;
    private final AttachmentService attachmentService;
    private final UnreadService unreadService;
    private final MessageBroadcaster broadcaster;

    public MessageService(MessageRepository messageRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository,
                          RoomService roomService,
                          ConversationService conversationService,
                          DialogService dialogService,
                          AttachmentService attachmentService,
                          UnreadService unreadService,
                          MessageBroadcaster broadcaster) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.roomService = roomService;
        this.conversationService = conversationService;
        this.dialogService = dialogService;
        this.attachmentService = attachmentService;
        this.unreadService = unreadService;
        this.broadcaster = broadcaster;
    }

    public MessageView post(UUID userId, UUID roomId, SendMessageRequest request) {
        roomService.requireMember(roomId, userId);
        Room room = roomService.requireRoom(roomId);
        UUID conversationId = room.getConversationId();
        validateBody(request);
        if (request.replyToId() != null) {
            Message parent = messageRepository.findById(request.replyToId())
                    .orElseThrow(() -> new NoSuchElementException("Reply target not found"));
            if (!Objects.equals(parent.getConversationId(), conversationId)) {
                throw new IllegalArgumentException("Reply target is in a different conversation");
            }
        }
        long seq = conversationService.assignNextSeq(conversationId);
        Message saved = messageRepository.save(
                new Message(conversationId, seq, userId, request.hasText() ? request.text() : "", request.replyToId()));
        attachmentService.linkToMessage(request.attachmentIds(), conversationId, userId, saved.getId());
        unreadService.bumpForMessage(conversationId, userId, seq);
        MessageView view = toView(saved, room);
        broadcaster.publish(WsEventEnvelope.EVENT_CREATED, roomId, view);
        return view;
    }

    public MessageView edit(UUID userId, UUID messageId, EditMessageRequest request) {
        Message message = requireMessage(messageId);
        if (!userId.equals(message.getAuthorId())) {
            throw new ForbiddenException("Only the author can edit this message");
        }
        if (message.isDeleted()) {
            throw new AccountConflictException("Cannot edit a deleted message");
        }
        if (request.text().getBytes(StandardCharsets.UTF_8).length > BODY_MAX_BYTES) {
            throw new IllegalArgumentException("Message body exceeds 3072-byte cap");
        }
        Conversation conversation = conversationService.require(message.getConversationId());
        Room room = null;
        if (Conversation.TYPE_DIALOG.equals(conversation.getType())) {
            dialogService.assertCanMutate(message.getConversationId());
        } else {
            room = requireRoomForConversation(message.getConversationId());
        }
        message.setBody(request.text());
        message.markEdited();
        MessageView view = toView(message, room);
        if (room != null) {
            broadcaster.publish(WsEventEnvelope.EVENT_EDITED, room.getId(), view);
        } else {
            broadcaster.publishToDialog(WsEventEnvelope.EVENT_EDITED, message.getConversationId(), view);
        }
        return view;
    }

    public void delete(UUID userId, UUID messageId) {
        Message message = requireMessage(messageId);
        if (message.isDeleted()) {
            return;
        }
        Conversation conversation = conversationService.require(message.getConversationId());
        Room room = null;
        if (Conversation.TYPE_DIALOG.equals(conversation.getType())) {
            Dialog dialog = dialogService.require(message.getConversationId());
            if (!dialog.hasParticipant(userId)) {
                throw new ForbiddenException("Not a participant");
            }
            dialogService.assertCanMutate(message.getConversationId());
            if (!userId.equals(message.getAuthorId())) {
                throw new ForbiddenException("Only the author can delete this message");
            }
        } else {
            room = requireRoomForConversation(message.getConversationId());
            boolean isAuthor = userId.equals(message.getAuthorId());
            if (!isAuthor) {
                roomService.requireAdmin(room.getId(), userId);
            }
        }
        message.markDeleted();
        MessageView view = toView(message, room);
        if (room != null) {
            broadcaster.publish(WsEventEnvelope.EVENT_DELETED, room.getId(), view);
        } else {
            broadcaster.publishToDialog(WsEventEnvelope.EVENT_DELETED, message.getConversationId(), view);
        }
    }

    public MessageView postToDialog(UUID userId, UUID conversationId, SendMessageRequest request) {
        dialogService.assertCanSend(conversationId, userId);
        validateBody(request);
        if (request.replyToId() != null) {
            Message parent = messageRepository.findById(request.replyToId())
                    .orElseThrow(() -> new NoSuchElementException("Reply target not found"));
            if (!Objects.equals(parent.getConversationId(), conversationId)) {
                throw new IllegalArgumentException("Reply target is in a different conversation");
            }
        }
        long seq = conversationService.assignNextSeq(conversationId);
        Message saved = messageRepository.save(
                new Message(conversationId, seq, userId, request.hasText() ? request.text() : "", request.replyToId()));
        attachmentService.linkToMessage(request.attachmentIds(), conversationId, userId, saved.getId());
        unreadService.bumpForMessage(conversationId, userId, seq);
        MessageView view = toView(saved, null);
        broadcaster.publishToDialog(WsEventEnvelope.EVENT_CREATED, conversationId, view);
        return view;
    }

    private void validateBody(SendMessageRequest request) {
        if (!request.hasText() && !request.hasAttachments()) {
            throw new IllegalArgumentException("Message must have text or attachments");
        }
        if (request.hasText()
                && request.text().getBytes(StandardCharsets.UTF_8).length > BODY_MAX_BYTES) {
            throw new IllegalArgumentException("Message body exceeds 3072-byte cap");
        }
    }

    @Transactional(readOnly = true)
    public HistoryPage historyForDialog(UUID conversationId, UUID userId,
                                        Long beforeSeq, Integer limit) {
        dialogService.assertReadable(conversationId, userId);
        int pageSize = resolvePageSize(limit);
        List<Message> rows = fetchRows(conversationId, beforeSeq, pageSize);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) rows = rows.subList(0, pageSize);
        return new HistoryPage(toViews(rows, null), hasMore);
    }

    @Transactional(readOnly = true)
    public HistoryPage history(UUID roomId, UUID userId, Long beforeSeq, Integer limit) {
        roomService.requireMember(roomId, userId);
        Room room = roomService.requireRoom(roomId);
        int pageSize = resolvePageSize(limit);
        List<Message> rows = fetchRows(room.getConversationId(), beforeSeq, pageSize);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) rows = rows.subList(0, pageSize);
        return new HistoryPage(toViews(rows, room), hasMore);
    }

    private int resolvePageSize(Integer limit) {
        return limit == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
    }

    private List<Message> fetchRows(UUID conversationId, Long beforeSeq, int pageSize) {
        // Ask for one extra row to detect whether older history remains.
        PageRequest page = PageRequest.of(0, pageSize + 1);
        return beforeSeq == null
                ? messageRepository.findLatest(conversationId, page)
                : messageRepository.findHistory(conversationId, beforeSeq, page);
    }

    private Message requireMessage(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new NoSuchElementException("Message not found"));
    }

    private Room requireRoomForConversation(UUID conversationId) {
        return roomRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new BadCredentialsException("Room not found for conversation"));
    }

    private MessageView toView(Message message, Room room) {
        return toViews(List.of(message), room).get(0);
    }

    private List<MessageView> toViews(List<Message> messages, Room room) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = new ArrayList<>();
        List<UUID> replyIds = new ArrayList<>();
        for (Message m : messages) {
            if (m.getAuthorId() != null) {
                userIds.add(m.getAuthorId());
            }
            if (m.getReplyToId() != null) {
                replyIds.add(m.getReplyToId());
            }
        }
        Map<UUID, String> usernames = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));
        }
        Map<UUID, Message> parents = new HashMap<>();
        if (!replyIds.isEmpty()) {
            messageRepository.findAllById(replyIds).forEach(m -> parents.put(m.getId(), m));
        }
        for (Message parent : parents.values()) {
            if (parent.getAuthorId() != null) {
                userIds.add(parent.getAuthorId());
            }
        }
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));
        }
        List<UUID> messageIds = new ArrayList<>(messages.size());
        for (Message m : messages) {
            if (m.getId() != null) {
                messageIds.add(m.getId());
            }
        }
        Map<UUID, List<AttachmentRef>> attachments = messageIds.isEmpty()
                ? Map.of()
                : attachmentService.refsByMessage(messageIds);

        List<MessageView> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            MessageView.ReplyRef ref = null;
            if (m.getReplyToId() != null) {
                Message parent = parents.get(m.getReplyToId());
                if (parent != null) {
                    String parentAuthor = parent.getAuthorId() == null
                            ? "(deleted)"
                            : usernames.getOrDefault(parent.getAuthorId(), "(deleted)");
                    String preview = parent.isDeleted()
                            ? null
                            : trimPreview(parent.getBody());
                    ref = new MessageView.ReplyRef(parent.getId(), parent.getSeq(), parentAuthor, preview);
                }
            }
            List<AttachmentRef> refs = m.getId() == null
                    ? List.of()
                    : attachments.getOrDefault(m.getId(), List.of());
            out.add(new MessageView(
                    m.getId(),
                    m.getConversationId(),
                    room == null ? null : room.getId(),
                    m.getSeq(),
                    m.getAuthorId(),
                    m.getAuthorId() == null ? "(deleted)" : usernames.getOrDefault(m.getAuthorId(), "(deleted)"),
                    m.isDeleted() ? null : m.getBody(),
                    ref,
                    refs,
                    m.getCreatedAt(),
                    m.getEditedAt(),
                    m.getDeletedAt()));
        }
        return out;
    }

    private static String trimPreview(String body) {
        if (body.length() <= PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, PREVIEW_LENGTH) + "…";
    }

    // Exposed for the Conversation boot step at room creation.
    Conversation ignored(Conversation c) {
        return c;
    }
}
