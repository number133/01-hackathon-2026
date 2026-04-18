package com.hackathon.chat.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameLower(String usernameLower);

    boolean existsByEmail(String email);

    boolean existsByUsernameLower(String usernameLower);
}
