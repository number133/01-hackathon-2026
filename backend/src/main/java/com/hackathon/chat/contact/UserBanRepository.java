package com.hackathon.chat.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBanRepository extends JpaRepository<UserBan, UserBanId> {

    boolean existsByOwnerIdAndTargetId(UUID ownerId, UUID targetId);

    Optional<UserBan> findByOwnerIdAndTargetId(UUID ownerId, UUID targetId);

    List<UserBan> findAllByOwnerId(UUID ownerId);
}
