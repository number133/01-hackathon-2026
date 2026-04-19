package com.hackathon.chat.invitation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.DuplicateResourceException;
import com.hackathon.chat.common.ForbiddenException;
import com.hackathon.chat.room.RoomBanRepository;
import com.hackathon.chat.room.RoomMember;
import com.hackathon.chat.room.RoomMemberRepository;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InvitationServiceTest {

    private RoomInvitationRepository invitationRepo;
    private RoomRepository roomRepo;
    private RoomMemberRepository memberRepo;
    private RoomBanRepository banRepo;
    private UserRepository userRepo;
    private RoomService roomService;
    private InvitationService service;

    @BeforeEach
    void setUp() {
        invitationRepo = Mockito.mock(RoomInvitationRepository.class);
        roomRepo = Mockito.mock(RoomRepository.class);
        memberRepo = Mockito.mock(RoomMemberRepository.class);
        banRepo = Mockito.mock(RoomBanRepository.class);
        userRepo = Mockito.mock(UserRepository.class);
        roomService = Mockito.mock(RoomService.class);
        service = new InvitationService(invitationRepo, roomRepo, memberRepo, banRepo, userRepo, roomService,
                Mockito.mock(com.hackathon.chat.conversation.ConversationService.class),
                Mockito.mock(com.hackathon.chat.unread.UnreadService.class));
    }

    @Test
    void inviteRejectsNonMemberActor() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(roomService.requireMember(roomId, actorId))
                .thenThrow(new ForbiddenException("not a member"));

        assertThatThrownBy(() -> service.invite(roomId, actorId,
                new CreateInvitationRequest("target", "msg")))
                .isInstanceOf(ForbiddenException.class);

        verify(invitationRepo, never()).save(any());
    }

    @Test
    void inviteRejectsAlreadyMemberTarget() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User target = new User("t@example.com", "target", "hash");
        when(roomService.requireMember(roomId, actorId))
                .thenReturn(new RoomMember(roomId, actorId, RoomMember.ROLE_MEMBER));
        when(userRepo.findByUsernameLower("target")).thenReturn(Optional.of(target));
        when(memberRepo.existsByRoomIdAndUserId(roomId, target.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.invite(roomId, actorId,
                new CreateInvitationRequest("target", null)))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void inviteRejectsBannedTarget() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User target = new User("t@example.com", "target", "hash");
        when(roomService.requireMember(roomId, actorId))
                .thenReturn(new RoomMember(roomId, actorId, RoomMember.ROLE_MEMBER));
        when(userRepo.findByUsernameLower("target")).thenReturn(Optional.of(target));
        when(banRepo.existsByRoomIdAndUserId(roomId, target.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.invite(roomId, actorId,
                new CreateInvitationRequest("target", null)))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void inviteRejectsDuplicatePending() {
        UUID roomId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User target = new User("t@example.com", "target", "hash");
        when(roomService.requireMember(roomId, actorId))
                .thenReturn(new RoomMember(roomId, actorId, RoomMember.ROLE_MEMBER));
        when(userRepo.findByUsernameLower("target")).thenReturn(Optional.of(target));
        when(invitationRepo.findByRoomIdAndInviteeUserIdAndStatus(
                roomId, target.getId(), RoomInvitation.STATUS_PENDING))
                .thenReturn(Optional.of(new RoomInvitation(roomId, target.getId(), actorId, null)));

        assertThatThrownBy(() -> service.invite(roomId, actorId,
                new CreateInvitationRequest("target", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void acceptAddsMembership() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RoomInvitation invite = new RoomInvitation(roomId, userId, UUID.randomUUID(), null);
        when(invitationRepo.findById(any())).thenReturn(Optional.of(invite));

        service.accept(UUID.randomUUID(), userId);

        verify(memberRepo).save(any(RoomMember.class));
    }

    @Test
    void declineMarksInviteDeclined() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RoomInvitation invite = new RoomInvitation(roomId, userId, UUID.randomUUID(), null);
        when(invitationRepo.findById(any())).thenReturn(Optional.of(invite));

        service.decline(UUID.randomUUID(), userId);

        verify(memberRepo, never()).save(any(RoomMember.class));
    }

    @Test
    void revokeRejectsNonInviter() {
        UUID roomId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        RoomInvitation invite = new RoomInvitation(roomId, UUID.randomUUID(), inviterId, null);
        when(invitationRepo.findById(any())).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.revoke(UUID.randomUUID(), otherId))
                .isInstanceOf(ForbiddenException.class);
    }
}
