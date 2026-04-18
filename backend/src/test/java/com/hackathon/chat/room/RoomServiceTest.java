package com.hackathon.chat.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.common.DuplicateResourceException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.user.UserRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

class RoomServiceTest {

    private RoomRepository roomRepo;
    private RoomMemberRepository memberRepo;
    private UserRepository userRepo;
    private RoomService service;

    @BeforeEach
    void setUp() {
        roomRepo = Mockito.mock(RoomRepository.class);
        memberRepo = Mockito.mock(RoomMemberRepository.class);
        userRepo = Mockito.mock(UserRepository.class);
        service = new RoomService(roomRepo, memberRepo, userRepo);
    }

    @Test
    void createInsertsOwnerMember() {
        UUID ownerId = UUID.randomUUID();
        when(roomRepo.saveAndFlush(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(ownerId, new CreateRoomRequest("general", "chat", "public"));

        verify(roomRepo).saveAndFlush(any(Room.class));
        verify(memberRepo).save(any(RoomMember.class));
    }

    @Test
    void createRejectsDuplicateName() {
        UUID ownerId = UUID.randomUUID();
        when(roomRepo.saveAndFlush(any(Room.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(ownerId,
                new CreateRoomRequest("general", "chat", "public")))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting("field").isEqualTo("name");

        verify(memberRepo, never()).save(any(RoomMember.class));
    }

    @Test
    void updateRejectsNonOwner() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RoomMember memberRole = new RoomMember(roomId, actorId, RoomMember.ROLE_MEMBER);
        when(memberRepo.findByRoomIdAndUserId(roomId, actorId)).thenReturn(Optional.of(memberRole));

        assertThatThrownBy(() -> service.update(roomId, actorId,
                new UpdateRoomRequest("new", null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateRejectsDuplicateName() {
        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Room room = new Room("general", "", "public", ownerId);
        RoomMember ownerMember = new RoomMember(roomId, ownerId, RoomMember.ROLE_OWNER);
        when(memberRepo.findByRoomIdAndUserId(roomId, ownerId)).thenReturn(Optional.of(ownerMember));
        when(roomRepo.findById(roomId)).thenReturn(Optional.of(room));
        when(roomRepo.saveAndFlush(any(Room.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.update(roomId, ownerId,
                new UpdateRoomRequest("taken", null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteRejectsNonOwner() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RoomMember memberRole = new RoomMember(roomId, actorId, RoomMember.ROLE_ADMIN);
        when(memberRepo.findByRoomIdAndUserId(roomId, actorId)).thenReturn(Optional.of(memberRole));

        assertThatThrownBy(() -> service.delete(roomId, actorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getForViewerHidesPrivateRoomsFromNonMembers() {
        UUID roomId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        Room room = new Room("secret", "", "private", UUID.randomUUID());
        when(roomRepo.findById(roomId)).thenReturn(Optional.of(room));
        when(memberRepo.existsByRoomIdAndUserId(roomId, viewerId)).thenReturn(false);

        assertThatThrownBy(() -> service.getForViewer(roomId, viewerId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void requireAdminRejectsPlainMember() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(memberRepo.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(new RoomMember(roomId, userId, RoomMember.ROLE_MEMBER)));

        assertThatThrownBy(() -> service.requireAdmin(roomId, userId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireMemberRejectsNonMember() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(memberRepo.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireMember(roomId, userId))
                .isInstanceOf(ForbiddenException.class);
    }
}
