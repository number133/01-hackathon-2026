package com.hackathon.chat.room;

import com.hackathon.chat.attachment.AttachmentService;
import com.hackathon.chat.common.DuplicateResourceException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.conversation.Conversation;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.unread.UnreadService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final AttachmentService attachmentService;
    private final UnreadService unreadService;

    public RoomService(RoomRepository roomRepository,
                       RoomMemberRepository memberRepository,
                       UserRepository userRepository,
                       ConversationService conversationService,
                       @Lazy AttachmentService attachmentService,
                       @Lazy UnreadService unreadService) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.attachmentService = attachmentService;
        this.unreadService = unreadService;
    }

    public Room create(UUID ownerId, CreateRoomRequest request) {
        Room room = new Room(
                request.name().trim(),
                request.description() == null ? "" : request.description().trim(),
                request.visibility(),
                ownerId);
        Conversation conversation = conversationService.create(Conversation.TYPE_ROOM);
        room.setConversationId(conversation.getId());
        try {
            Room saved = roomRepository.saveAndFlush(room);
            memberRepository.save(new RoomMember(saved.getId(), ownerId, RoomMember.ROLE_OWNER));
            unreadService.initMarker(ownerId, conversation.getId(), 0L);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("name", "Room name already taken");
        }
    }

    public Room update(UUID roomId, UUID actorId, UpdateRoomRequest patch) {
        Room room = requireOwner(roomId, actorId).room();
        if (patch.name() != null) {
            room.setName(patch.name().trim());
        }
        if (patch.description() != null) {
            room.setDescription(patch.description().trim());
        }
        if (patch.visibility() != null) {
            room.setVisibility(patch.visibility());
        }
        try {
            return roomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("name", "Room name already taken");
        }
    }

    public void delete(UUID roomId, UUID actorId) {
        OwnerContext ctx = requireOwner(roomId, actorId);
        deleteConversationAndRoom(ctx.room());
    }

    /**
     * Called by UserService during account deletion to cascade owned rooms.
     * Skips the owner authorisation check — the caller has already verified
     * the user is the owner.
     */
    public void deleteAsOwnerCascade(UUID roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return;
        }
        deleteConversationAndRoom(room);
    }

    /**
     * Deletes the conversation which cascades to the room (via the FK on
     * room.conversation_id) and to every message in that conversation. Order
     * matters — deleting the room first would leave the conversation and its
     * messages orphaned.
     */
    private void deleteConversationAndRoom(Room room) {
        UUID conversationId = room.getConversationId();
        // Delete the room row explicitly in case an older row predates the
        // V5 backfill and has no conversation_id.
        roomRepository.deleteById(room.getId());
        if (conversationId != null) {
            conversationService.deleteConversation(conversationId);
            attachmentService.deleteConversationTree(conversationId);
        }
    }

    public List<Room> findOwnedBy(UUID ownerId) {
        return roomRepository.findAllByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public List<RoomView> listPublicCatalog(String q, int limit, UUID viewerId) {
        String pattern = (q == null || q.isBlank())
                ? "%"
                : "%" + q.trim().toLowerCase() + "%";
        List<Room> rooms = roomRepository.searchPublic(pattern,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
        return toViews(rooms, viewerId);
    }

    @Transactional(readOnly = true)
    public RoomView getForViewer(UUID roomId, UUID viewerId) {
        Room room = roomRepository.findById(roomId).orElseThrow(
                () -> new NoSuchElementException("Room not found"));
        boolean isMember = memberRepository.existsByRoomIdAndUserId(roomId, viewerId);
        if (!room.isPublic() && !isMember) {
            // Hide private rooms from non-members behind 404 instead of 403
            // to avoid leaking existence (phase_2_plan §11).
            throw new NoSuchElementException("Room not found");
        }
        return toViews(List.of(room), viewerId).get(0);
    }

    @Transactional(readOnly = true)
    public RoomMember requireMember(UUID roomId, UUID userId) {
        return memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this room"));
    }

    @Transactional(readOnly = true)
    public OwnerContext requireOwner(UUID roomId, UUID userId) {
        RoomMember member = requireMember(roomId, userId);
        if (!member.isOwner()) {
            throw new ForbiddenException("Only the room owner can perform this action");
        }
        Room room = roomRepository.findById(roomId).orElseThrow(
                () -> new NoSuchElementException("Room not found"));
        return new OwnerContext(room, member);
    }

    @Transactional(readOnly = true)
    public RoomMember requireAdmin(UUID roomId, UUID userId) {
        RoomMember member = requireMember(roomId, userId);
        if (!member.isAdminOrOwner()) {
            throw new ForbiddenException("Only admins or the owner can perform this action");
        }
        return member;
    }

    public Room requireRoom(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow(
                () -> new NoSuchElementException("Room not found"));
    }

    private List<RoomView> toViews(List<Room> rooms, UUID viewerId) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<UUID> ownerIds = rooms.stream().map(Room::getOwnerId).toList();
        Map<UUID, String> usernames = new HashMap<>();
        userRepository.findAllById(ownerIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));

        List<RoomView> views = new ArrayList<>(rooms.size());
        for (Room r : rooms) {
            long count = memberRepository.countByRoomId(r.getId());
            Optional<RoomMember> viewerMembership = viewerId == null
                    ? Optional.empty()
                    : memberRepository.findByRoomIdAndUserId(r.getId(), viewerId);
            String myRole = viewerMembership.map(RoomMember::getRole).orElse(null);
            views.add(new RoomView(
                    r.getId(),
                    r.getConversationId(),
                    r.getName(),
                    r.getDescription(),
                    r.getVisibility(),
                    r.getOwnerId(),
                    usernames.getOrDefault(r.getOwnerId(), "(deleted)"),
                    count,
                    myRole,
                    r.getCreatedAt()));
        }
        return views;
    }

    public record OwnerContext(Room room, RoomMember member) {
    }

    public String ownerUsername(User user) {
        return user.getUsername();
    }
}
