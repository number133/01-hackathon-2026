package com.hackathon.chat.dev;

import com.hackathon.chat.auth.RegistrationRequest;
import com.hackathon.chat.dialog.DialogService;
import com.hackathon.chat.dialog.DialogView;
import com.hackathon.chat.invitation.CreateInvitationRequest;
import com.hackathon.chat.invitation.InvitationService;
import com.hackathon.chat.message.MessageService;
import com.hackathon.chat.message.SendMessageRequest;
import com.hackathon.chat.room.CreateRoomRequest;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomMembershipService;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import com.hackathon.chat.contact.FriendRequestStatus;
import com.hackathon.chat.contact.FriendRequestView;
import com.hackathon.chat.contact.FriendService;
import com.hackathon.chat.user.User;
import com.hackathon.chat.user.UserRepository;
import com.hackathon.chat.user.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "chat.demo.seed-enabled", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String SEED_PASSWORD = "DemoPass123!";
    public static final String ALICE_EMAIL = "alice@demo.test";
    public static final String BOB_EMAIL = "bob@demo.test";
    public static final String CAROL_EMAIL = "carol@demo.test";
    public static final String ALICE_USERNAME = "alice";
    public static final String BOB_USERNAME = "bob";
    public static final String CAROL_USERNAME = "carol";
    public static final String PUBLIC_ROOM_NAME = "general-demo";
    public static final String PRIVATE_ROOM_NAME = "ops-demo";

    public static final List<DemoAccount> SEED_ACCOUNTS = List.of(
            new DemoAccount(ALICE_EMAIL, ALICE_USERNAME, SEED_PASSWORD),
            new DemoAccount(BOB_EMAIL, BOB_USERNAME, SEED_PASSWORD),
            new DemoAccount(CAROL_EMAIL, CAROL_USERNAME, SEED_PASSWORD));

    private final UserRepository userRepository;
    private final UserService userService;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomMembershipService membershipService;
    private final InvitationService invitationService;
    private final FriendService friendService;
    private final DialogService dialogService;
    private final MessageService messageService;

    public DemoDataSeeder(UserRepository userRepository,
                          UserService userService,
                          RoomService roomService,
                          RoomRepository roomRepository,
                          RoomMembershipService membershipService,
                          InvitationService invitationService,
                          FriendService friendService,
                          DialogService dialogService,
                          MessageService messageService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.membershipService = membershipService;
        this.invitationService = invitationService;
        this.friendService = friendService;
        this.dialogService = dialogService;
        this.messageService = messageService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            seed();
        } catch (RuntimeException ex) {
            log.warn("demo seed failed — continuing boot: {}", ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seed() {
        User alice = ensureUser(ALICE_EMAIL, ALICE_USERNAME);
        User bob = ensureUser(BOB_EMAIL, BOB_USERNAME);
        User carol = ensureUser(CAROL_EMAIL, CAROL_USERNAME);

        boolean seededAnything = false;
        seededAnything |= seedPublicRoom(alice, bob);
        seededAnything |= seedPrivateRoom(alice, carol);
        seededAnything |= seedFriendshipAndDialog(alice, bob);
        seededAnything |= seedPendingFriendRequest(carol, bob);

        if (seededAnything) {
            log.info("demo seed complete — alice/bob/carol with general-demo, ops-demo, dialog");
        } else {
            log.info("demo seed skipped — already present");
        }
    }

    private User ensureUser(String email, String username) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        return userService.register(new RegistrationRequest(email, username, SEED_PASSWORD));
    }

    private boolean seedPublicRoom(User alice, User bob) {
        if (roomRepository.findByName(PUBLIC_ROOM_NAME).isPresent()) {
            return false;
        }
        Room room = roomService.create(alice.getId(),
                new CreateRoomRequest(PUBLIC_ROOM_NAME,
                        "Open lounge for the jury to poke at.", "public"));
        membershipService.join(room.getId(), bob.getId());

        List<UUID> authors = List.of(alice.getId(), bob.getId(), alice.getId(), bob.getId(), alice.getId());
        List<String> bodies = List.of(
                "Welcome to the demo room.",
                "Thanks! Posting to show the watermark bump in action.",
                "Feel free to edit or delete any of these messages.",
                "Attachments work too — drag one in on the composer.",
                "Head to DMs once you're ready to test the dialog path.");
        for (int i = 0; i < authors.size(); i++) {
            messageService.post(authors.get(i), room.getId(),
                    new SendMessageRequest(bodies.get(i), null, null));
        }
        return true;
    }

    private boolean seedPrivateRoom(User alice, User carol) {
        if (roomRepository.findByName(PRIVATE_ROOM_NAME).isPresent()) {
            return false;
        }
        Room room = roomService.create(alice.getId(),
                new CreateRoomRequest(PRIVATE_ROOM_NAME,
                        "Invite-only room — Carol has a pending invite.", "private"));
        invitationService.invite(room.getId(), alice.getId(),
                new CreateInvitationRequest(carol.getUsername(), "Join us in ops-demo."));
        return true;
    }

    private boolean seedFriendshipAndDialog(User alice, User bob) {
        boolean created = false;
        if (!friendService.areFriends(alice.getId(), bob.getId())) {
            FriendRequestView req = friendService.sendRequest(alice.getId(), bob.getUsername(),
                    "Let's connect for the demo.");
            friendService.accept(req.id(), bob.getId());
            created = true;
        }
        DialogView dialog = dialogService.getOrCreate(alice.getId(), bob.getId());
        UUID convId = dialog.id();
        if (hasMessages(convId, alice)) {
            return created;
        }
        messageService.postToDialog(alice.getId(), convId,
                new SendMessageRequest("Hey Bob — ready for the demo?", null, null));
        messageService.postToDialog(bob.getId(), convId,
                new SendMessageRequest("All set. Let's show off the unread badges.", null, null));
        messageService.postToDialog(alice.getId(), convId,
                new SendMessageRequest("On it — opening the catalog now.", null, null));
        return true;
    }

    private boolean hasMessages(UUID conversationId, User viewer) {
        return !messageService
                .historyForDialog(conversationId, viewer.getId(), null, 1)
                .items()
                .isEmpty();
    }

    private boolean seedPendingFriendRequest(User carol, User bob) {
        if (friendService.areFriends(carol.getId(), bob.getId())) {
            return false;
        }
        boolean hasPending = friendService.list(carol.getId(), "outgoing").stream()
                .anyMatch(fr -> fr.recipient() != null
                        && bob.getId().equals(fr.recipient().id())
                        && fr.status() == FriendRequestStatus.PENDING);
        if (hasPending) {
            return false;
        }
        friendService.sendRequest(carol.getId(), bob.getUsername(),
                "Hi Bob, can we chat?");
        return true;
    }
}
