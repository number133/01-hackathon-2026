package com.hackathon.chat.dialog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DialogRepository extends JpaRepository<Dialog, UUID> {

    Optional<Dialog> findByUserAIdAndUserBId(UUID userAId, UUID userBId);

    @Query("SELECT d FROM Dialog d WHERE d.userAId = :userId OR d.userBId = :userId")
    List<Dialog> findAllTouching(@Param("userId") UUID userId);
}
