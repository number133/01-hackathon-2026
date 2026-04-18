package com.hackathon.chat.user;

import com.hackathon.chat.auth.RegistrationRequest;
import com.hackathon.chat.common.AccountConflictException;
import com.hackathon.chat.common.DuplicateResourceException;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        // Phase 2 will cascade-delete rooms owned by the user. For now the user
        // cannot own any room, so this check always passes.
        if (countRoomsOwnedBy(userId) > 0) {
            throw new AccountConflictException(
                    "Delete or transfer rooms you own before deleting your account.");
        }
        userRepository.delete(user);
    }

    // Phase 2 will replace this with a RoomRepository call.
    long countRoomsOwnedBy(UUID userId) {
        return 0;
    }
}
