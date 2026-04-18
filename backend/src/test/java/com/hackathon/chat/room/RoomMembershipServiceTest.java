package com.hackathon.chat.room;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.user.UserRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RoomMembershipServiceTest {

    private RoomService roomService;
    private RoomRepository roomRepo;
    private RoomMemberRepository memberRepo;
    private RoomBanRepository banRepo;
    private UserRepository userRepo;
    private RoomMembershipService service;

    @BeforeEach
    void setUp() {
        roomService = Mockito.mock(RoomService.class);
        roomRepo = Mockito.mock(RoomRepository.class);
        memberRepo = Mockito.mock(RoomMemberRepository.class);
        banRepo = Mockito.mock(RoomBanRepository.class);
        userRepo = Mockito.mock(UserRepository.class);
        service = new RoomMembershipService(roomService, roomRepo, memberRepo, banRepo, userRepo);
    }

    @Test
    void joinRejectsPrivateRoom() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = new Room("private-room", "", "private", UUID.randomUUID());
        when(roomService.requireRoom(roomId)).thenReturn(room);

        assertThatThrownBy(() -> service.join(roomId, userId))
                .isInstanceOf(ForbiddenException.class);

        verify(memberRepo, never()).save(any(RoomMember.class));
    }

    @Test
    void joinRejectsBannedUser() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = new Room("pub", "", "public", UUID.randomUUID());
        when(roomService.requireRoom(roomId)).thenReturn(room);
        when(banRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        assertThatThrownBy(() -> service.join(roomId, userId))
                .isInstanceOf(ForbiddenException.class);

        verify(memberRepo, never()).save(any(RoomMember.class));
    }

    @Test
    void joinRejectsAlreadyMember() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = new Room("pub", "", "public", UUID.randomUUID());
        when(roomService.requireRoom(roomId)).thenReturn(room);
        when(banRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);
        when(memberRepo.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        assertThatThrownBy(() -> service.join(roomId, userId))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void joinInsertsMemberRow() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = new Room("pub", "", "public", UUID.randomUUID());
        when(roomService.requireRoom(roomId)).thenReturn(room);

        service.join(roomId, userId);

        verify(memberRepo).save(any(RoomMember.class));
    }

    @Test
    void leaveRejectsOwner() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(roomService.requireMember(roomId, userId))
                .thenReturn(new RoomMember(roomId, userId, RoomMember.ROLE_OWNER));

        assertThatThrownBy(() -> service.leave(roomId, userId))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void banRejectsOwnerTarget() {
        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Room room = new Room("r", "", "public", ownerId);
        when(roomService.requireAdmin(roomId, actorId))
                .thenReturn(new RoomMember(roomId, actorId, RoomMember.ROLE_ADMIN));
        when(roomService.requireRoom(roomId)).thenReturn(room);

        assertThatThrownBy(() -> service.ban(roomId, ownerId, actorId, "nope"))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void banRemovesMemberAndInsertsBan() {
        UUID roomId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Room room = new Room("r", "", "public", UUID.randomUUID());
        RoomMember adminActor = new RoomMember(roomId, actorId, RoomMember.ROLE_ADMIN);
        RoomMember targetMember = new RoomMember(roomId, targetId, RoomMember.ROLE_MEMBER);
        when(roomService.requireAdmin(roomId, actorId)).thenReturn(adminActor);
        when(roomService.requireRoom(roomId)).thenReturn(room);
        when(memberRepo.findByRoomIdAndUserId(roomId, targetId)).thenReturn(Optional.of(targetMember));
        when(banRepo.save(any(RoomBan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.ban(roomId, targetId, actorId, "bye");

        verify(memberRepo).delete(targetMember);
        verify(banRepo).save(any(RoomBan.class));
    }

    @Test
    void promoteRejectsNonOwnerActor() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(roomService.requireOwner(roomId, actorId))
                .thenThrow(new ForbiddenException("nope"));

        assertThatThrownBy(() -> service.promote(roomId, targetId, actorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void promoteRejectsNonMember() {
        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(roomService.requireOwner(roomId, ownerId))
                .thenReturn(new RoomService.OwnerContext(
                        new Room("r", "", "public", ownerId),
                        new RoomMember(roomId, ownerId, RoomMember.ROLE_OWNER)));
        when(memberRepo.findByRoomIdAndUserId(roomId, targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promote(roomId, targetId, ownerId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void demoteRejectsOwnerTarget() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(roomService.requireAdmin(roomId, actorId))
                .thenReturn(new RoomMember(roomId, actorId, RoomMember.ROLE_OWNER));
        when(memberRepo.findByRoomIdAndUserId(roomId, targetId))
                .thenReturn(Optional.of(new RoomMember(roomId, targetId, RoomMember.ROLE_OWNER)));

        assertThatThrownBy(() -> service.demote(roomId, targetId, actorId))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void demoteRejectsNonAdminTarget() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(roomService.requireAdmin(roomId, actorId))
                .thenReturn(new RoomMember(roomId, actorId, RoomMember.ROLE_OWNER));
        when(memberRepo.findByRoomIdAndUserId(roomId, targetId))
                .thenReturn(Optional.of(new RoomMember(roomId, targetId, RoomMember.ROLE_MEMBER)));

        assertThatThrownBy(() -> service.demote(roomId, targetId, actorId))
                .isInstanceOf(AccountConflictException.class);
    }
}
