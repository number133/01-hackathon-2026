package com.hackathon.chat.room;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoomMembershipService {

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomBanRepository banRepository;
    private final UserRepository userRepository;

    public RoomMembershipService(RoomService roomService,
                                 RoomRepository roomRepository,
                                 RoomMemberRepository memberRepository,
                                 RoomBanRepository banRepository,
                                 UserRepository userRepository) {
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.banRepository = banRepository;
        this.userRepository = userRepository;
    }

    public void join(UUID roomId, UUID userId) {
        Room room = roomService.requireRoom(roomId);
        if (!room.isPublic()) {
            throw new ForbiddenException("Private rooms require an invitation");
        }
        if (banRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ForbiddenException("You are banned from this room");
        }
        if (memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new AccountConflictException("Already a member");
        }
        memberRepository.save(new RoomMember(roomId, userId, RoomMember.ROLE_MEMBER));
    }

    public void leave(UUID roomId, UUID userId) {
        RoomMember member = roomService.requireMember(roomId, userId);
        if (member.isOwner()) {
            throw new AccountConflictException(
                    "Owners cannot leave their own room — delete the room instead");
        }
        memberRepository.deleteByRoomIdAndUserId(roomId, userId);
    }

    public void remove(UUID roomId, UUID targetUserId, UUID actorUserId) {
        RoomMember actor = roomService.requireAdmin(roomId, actorUserId);
        if (actor.getUserId().equals(targetUserId)) {
            throw new AccountConflictException("Use 'leave' to remove yourself");
        }
        Room room = roomService.requireRoom(roomId);
        if (room.getOwnerId().equals(targetUserId)) {
            throw new AccountConflictException("The room owner cannot be removed");
        }
        RoomMember target = memberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Target user is not a member"));
        memberRepository.delete(target);
    }

    public RoomBan ban(UUID roomId, UUID targetUserId, UUID actorUserId, String reason) {
        RoomMember actor = roomService.requireAdmin(roomId, actorUserId);
        if (actor.getUserId().equals(targetUserId)) {
            throw new AccountConflictException("Use 'leave' to remove yourself");
        }
        Room room = roomService.requireRoom(roomId);
        if (room.getOwnerId().equals(targetUserId)) {
            throw new AccountConflictException("The room owner cannot be banned");
        }
        memberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .ifPresent(memberRepository::delete);
        return banRepository.save(new RoomBan(roomId, targetUserId, actorUserId, reason));
    }

    public void unban(UUID roomId, UUID targetUserId, UUID actorUserId) {
        roomService.requireAdmin(roomId, actorUserId);
        banRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .ifPresent(banRepository::delete);
    }

    public void promote(UUID roomId, UUID targetUserId, UUID actorUserId) {
        roomService.requireOwner(roomId, actorUserId);
        RoomMember target = memberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Target user is not a member"));
        if (!RoomMember.ROLE_MEMBER.equals(target.getRole())) {
            throw new AccountConflictException("Target is not a plain member");
        }
        target.setRole(RoomMember.ROLE_ADMIN);
    }

    public void demote(UUID roomId, UUID targetUserId, UUID actorUserId) {
        roomService.requireAdmin(roomId, actorUserId);
        RoomMember target = memberRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new NoSuchElementException("Target user is not a member"));
        if (target.isOwner()) {
            throw new AccountConflictException("The owner cannot lose admin rights");
        }
        if (!RoomMember.ROLE_ADMIN.equals(target.getRole())) {
            throw new AccountConflictException("Target is not an admin");
        }
        target.setRole(RoomMember.ROLE_MEMBER);
    }

    @Transactional(readOnly = true)
    public List<RoomMemberView> listMembers(UUID roomId, UUID viewerId) {
        roomService.requireMember(roomId, viewerId);
        List<RoomMember> members = memberRepository.findAllByRoomId(roomId);
        return toMemberViews(members);
    }

    @Transactional(readOnly = true)
    public List<RoomBanView> listBans(UUID roomId, UUID viewerId) {
        roomService.requireAdmin(roomId, viewerId);
        List<RoomBan> bans = banRepository.findAllByRoomId(roomId);
        if (bans.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = new ArrayList<>();
        for (RoomBan b : bans) {
            userIds.add(b.getUserId());
            if (b.getBannedBy() != null) {
                userIds.add(b.getBannedBy());
            }
        }
        Map<UUID, String> usernames = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> usernames.put(u.getId(), u.getUsername()));

        List<RoomBanView> views = new ArrayList<>(bans.size());
        for (RoomBan b : bans) {
            views.add(new RoomBanView(
                    b.getUserId(),
                    usernames.getOrDefault(b.getUserId(), "(deleted)"),
                    b.getBannedBy(),
                    b.getBannedBy() == null ? null : usernames.getOrDefault(b.getBannedBy(), "(deleted)"),
                    b.getReason(),
                    b.getBannedAt()));
        }
        return views;
    }

    private List<RoomMemberView> toMemberViews(List<RoomMember> members) {
        if (members.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = members.stream().map(RoomMember::getUserId).toList();
        Map<UUID, String> usernames = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> usernames.put(u.getId(), u.getUsername()));
        List<RoomMemberView> views = new ArrayList<>(members.size());
        for (RoomMember m : members) {
            views.add(new RoomMemberView(
                    m.getUserId(),
                    usernames.getOrDefault(m.getUserId(), "(deleted)"),
                    m.getRole(),
                    m.getJoinedAt()));
        }
        return views;
    }

    // Exposed so UserService can look up a username for logs if needed.
    User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }
}
