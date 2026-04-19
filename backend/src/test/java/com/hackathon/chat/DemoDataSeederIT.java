package com.hackathon.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.chat.contact.FriendRequestStatus;
import com.hackathon.chat.contact.FriendRequestView;
import com.hackathon.chat.contact.FriendService;
import com.hackathon.chat.dev.DemoDataSeeder;
import com.hackathon.chat.dialog.DialogService;
import com.hackathon.chat.dialog.DialogView;
import com.hackathon.chat.invitation.InvitationService;
import com.hackathon.chat.invitation.InvitationView;
import com.hackathon.chat.message.MessageService;
import com.hackathon.chat.message.MessageView;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "chat.demo.seed-enabled=true")
@Testcontainers
class DemoDataSeederIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private FriendService friendService;

    @Autowired
    private DialogService dialogService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private InvitationService invitationService;

    @Test
    void seedsThreeAccountsTwoRoomsFriendshipAndDialog() {
        User alice = userRepository.findByEmail(DemoDataSeeder.ALICE_EMAIL).orElseThrow();
        User bob = userRepository.findByEmail(DemoDataSeeder.BOB_EMAIL).orElseThrow();
        User carol = userRepository.findByEmail(DemoDataSeeder.CAROL_EMAIL).orElseThrow();

        Room publicRoom = roomRepository.findByName(DemoDataSeeder.PUBLIC_ROOM_NAME).orElseThrow();
        assertThat(publicRoom.isPublic()).isTrue();
        assertThat(publicRoom.getOwnerId()).isEqualTo(alice.getId());
        List<MessageView> history = messageService.history(publicRoom.getId(), alice.getId(), null, 100).items();
        assertThat(history).hasSizeGreaterThanOrEqualTo(5);

        Room privateRoom = roomRepository.findByName(DemoDataSeeder.PRIVATE_ROOM_NAME).orElseThrow();
        assertThat(privateRoom.isPublic()).isFalse();
        List<InvitationView> carolInvites = invitationService.listForInvitee(carol.getId());
        assertThat(carolInvites).anyMatch(v -> v.roomId().equals(privateRoom.getId()));

        assertThat(friendService.areFriends(alice.getId(), bob.getId())).isTrue();

        DialogView dialog = dialogService.getOrCreate(alice.getId(), bob.getId());
        List<MessageView> dmHistory = messageService
                .historyForDialog(dialog.id(), alice.getId(), null, 100)
                .items();
        assertThat(dmHistory).hasSizeGreaterThanOrEqualTo(3);

        List<FriendRequestView> carolOutgoing = friendService.list(carol.getId(), "outgoing");
        assertThat(carolOutgoing)
                .anyMatch(fr -> fr.recipient() != null
                        && fr.recipient().id().equals(bob.getId())
                        && fr.status() == FriendRequestStatus.PENDING);
    }

    @Test
    void reseedingIsIdempotent() {
        long usersBefore = userRepository.count();
        long roomsBefore = roomRepository.count();

        seeder.seed();
        seeder.seed();

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(roomRepository.count()).isEqualTo(roomsBefore);

        User alice = userRepository.findByEmail(DemoDataSeeder.ALICE_EMAIL).orElseThrow();
        User bob = userRepository.findByEmail(DemoDataSeeder.BOB_EMAIL).orElseThrow();
        Room publicRoom = roomRepository.findByName(DemoDataSeeder.PUBLIC_ROOM_NAME).orElseThrow();
        List<MessageView> roomHistory = messageService.history(publicRoom.getId(), alice.getId(), null, 100).items();
        assertThat(roomHistory).hasSize(5);

        DialogView dialog = dialogService.getOrCreate(alice.getId(), bob.getId());
        List<MessageView> dmHistory = messageService
                .historyForDialog(dialog.id(), alice.getId(), null, 100)
                .items();
        assertThat(dmHistory).hasSize(3);
    }
}
