package com.hackathon.chat.invitation;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.DuplicateResourceException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.conversation.ConversationService;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomBanRepository;
import com.hackathon.chat.room.RoomMember;
import com.hackathon.chat.room.RoomMemberRepository;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.unread.UnreadService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InvitationService {

    private final RoomInvitationRepository invitationRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomBanRepository banRepository;
    private final UserRepository userRepository;
    private final RoomService roomService;
    private final ConversationService conversationService;
    private final UnreadService unreadService;

    public InvitationService(RoomInvitationRepository invitationRepository,
                             RoomRepository roomRepository,
                             RoomMemberRepository memberRepository,
                             RoomBanRepository banRepository,
                             UserRepository userRepository,
                             RoomService roomService,
                             ConversationService conversationService,
                             @Lazy UnreadService unreadService) {
        this.invitationRepository = invitationRepository;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.banRepository = banRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.unreadService = unreadService;
        this.roomService = roomService;
    }

    public InvitationView invite(UUID roomId, UUID actorUserId, CreateInvitationRequest request) {
        roomService.requireMember(roomId, actorUserId);
        User target = userRepository.findByUsernameLower(request.username().toLowerCase())
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (Objects.equals(target.getId(), actorUserId)) {
            throw new AccountConflictException("You cannot invite yourself");
        }
        if (memberRepository.existsByRoomIdAndUserId(roomId, target.getId())) {
            throw new AccountConflictException("User is already a member");
        }
        if (banRepository.existsByRoomIdAndUserId(roomId, target.getId())) {
            throw new AccountConflictException("User is banned from this room");
        }
        if (invitationRepository
                .findByRoomIdAndInviteeUserIdAndStatus(roomId, target.getId(), RoomInvitation.STATUS_PENDING)
                .isPresent()) {
            throw new DuplicateResourceException("username", "Invitation already pending");
        }
        RoomInvitation saved = invitationRepository.save(
                new RoomInvitation(roomId, target.getId(), actorUserId, request.message()));
        return toView(saved);
    }

    public void revoke(UUID invitationId, UUID actorUserId) {
        RoomInvitation invite = require(invitationId);
        if (invite.getInviterUserId() == null || !invite.getInviterUserId().equals(actorUserId)) {
            throw new ForbiddenException("Only the inviter can revoke this invitation");
        }
        if (!invite.isPending()) {
            throw new AccountConflictException("Invitation is not pending");
        }
        invite.markRevoked();
    }

    public void accept(UUID invitationId, UUID userId) {
        RoomInvitation invite = require(invitationId);
        ensureInvitee(invite, userId);
        if (!invite.isPending()) {
            throw new AccountConflictException("Invitation is not pending");
        }
        if (banRepository.existsByRoomIdAndUserId(invite.getRoomId(), userId)) {
            throw new ForbiddenException("You are banned from this room");
        }
        if (!memberRepository.existsByRoomIdAndUserId(invite.getRoomId(), userId)) {
            memberRepository.save(new RoomMember(invite.getRoomId(), userId, RoomMember.ROLE_MEMBER));
            roomRepository.findById(invite.getRoomId())
                    .map(Room::getConversationId)
                    .ifPresent(convId -> {
                        long lastSeq = conversationService.require(convId).getLastSeq();
                        unreadService.initMarker(userId, convId, lastSeq);
                    });
        }
        invite.markAccepted();
    }

    public void decline(UUID invitationId, UUID userId) {
        RoomInvitation invite = require(invitationId);
        ensureInvitee(invite, userId);
        if (!invite.isPending()) {
            throw new AccountConflictException("Invitation is not pending");
        }
        invite.markDeclined();
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listForInvitee(UUID userId) {
        return toViews(invitationRepository.findAllByInviteeUserIdAndStatus(
                userId, RoomInvitation.STATUS_PENDING));
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listForRoom(UUID roomId, UUID actorUserId) {
        roomService.requireAdmin(roomId, actorUserId);
        return toViews(invitationRepository.findAllByRoomIdAndStatus(
                roomId, RoomInvitation.STATUS_PENDING));
    }

    private RoomInvitation require(UUID invitationId) {
        return invitationRepository.findById(invitationId).orElseThrow(
                () -> new NoSuchElementException("Invitation not found"));
    }

    private void ensureInvitee(RoomInvitation invite, UUID userId) {
        if (!invite.getInviteeUserId().equals(userId)) {
            throw new ForbiddenException("Invitation does not belong to you");
        }
    }

    private InvitationView toView(RoomInvitation invite) {
        return toViews(List.of(invite)).get(0);
    }

    private List<InvitationView> toViews(List<RoomInvitation> invites) {
        if (invites.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = new ArrayList<>();
        List<UUID> roomIds = new ArrayList<>();
        for (RoomInvitation i : invites) {
            userIds.add(i.getInviteeUserId());
            if (i.getInviterUserId() != null) {
                userIds.add(i.getInviterUserId());
            }
            roomIds.add(i.getRoomId());
        }
        Map<UUID, String> usernames = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));
        Map<UUID, String> roomNames = new HashMap<>();
        roomRepository.findAllById(roomIds).forEach(r -> roomNames.put(r.getId(), r.getName()));

        List<InvitationView> views = new ArrayList<>(invites.size());
        for (RoomInvitation i : invites) {
            views.add(new InvitationView(
                    i.getId(),
                    i.getRoomId(),
                    roomNames.getOrDefault(i.getRoomId(), "(deleted)"),
                    i.getInviterUserId(),
                    i.getInviterUserId() == null
                            ? null
                            : usernames.getOrDefault(i.getInviterUserId(), "(deleted)"),
                    i.getInviteeUserId(),
                    usernames.getOrDefault(i.getInviteeUserId(), "(deleted)"),
                    i.getMessage(),
                    i.getStatus(),
                    i.getCreatedAt(),
                    i.getResolvedAt()));
        }
        return views;
    }

    // Private rooms: service also needs access to the underlying room for
    // visibility checks in future phases; expose as a pass-through helper.
    Room requireRoom(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow(
                () -> new NoSuchElementException("Room not found"));
    }
}
