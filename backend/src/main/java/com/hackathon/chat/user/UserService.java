package com.hackathon.chat.user;

import com.hackathon.chat.auth.RegistrationRequest;
import com.hackathon.chat.common.DuplicateResourceException;
import com.hackathon.chat.room.Room;
import com.hackathon.chat.room.RoomRepository;
import com.hackathon.chat.room.RoomService;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private RoomRepository roomRepository;

    @Autowired(required = false)
    private RoomService roomService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegistrationRequest request) {
        String email = request.email().trim();
        String username = request.username().trim();
        String usernameLower = username.toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("email", "Email already registered");
        }
        if (userRepository.existsByUsernameLower(usernameLower)) {
            throw new DuplicateResourceException("username", "Username already taken");
        }

        User user = new User(email, username, passwordEncoder.encode(request.password()));
        return userRepository.save(user);
    }

    public User requireById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BadCredentialsException("user not found"));
    }

    public User requireByUsername(String username) {
        return userRepository.findByUsernameLower(username.toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("user not found"));
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = requireById(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("current password incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    public void deleteAccount(UUID userId, String password) {
        User user = requireById(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("password incorrect");
        }
        // Cascade: delete rooms the user owns first (which cascades their
        // members/bans/invites via FK ON DELETE CASCADE). Memberships in rooms
        // owned by other users are cleared by the users-row delete via the same
        // FK mechanism.
        if (roomRepository != null && roomService != null) {
            List<Room> owned = roomRepository.findAllByOwnerId(userId);
            for (Room r : owned) {
                roomService.deleteAsOwnerCascade(r.getId());
            }
        }
        userRepository.delete(user);
    }

    public long countRoomsOwnedBy(UUID userId) {
        return roomRepository == null ? 0 : roomRepository.countByOwnerId(userId);
    }
}
