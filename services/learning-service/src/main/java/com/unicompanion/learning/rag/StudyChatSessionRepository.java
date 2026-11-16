package com.unicompanion.learning.rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudyChatSessionRepository extends JpaRepository<StudyChatSessionEntity, UUID> {

    Optional<StudyChatSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<StudyChatSessionEntity> findFirstByCourseIdAndUserIdOrderByUpdatedAtDesc(UUID courseId, UUID userId);

    void deleteByCourseIdAndUserId(UUID courseId, UUID userId);

    void deleteByUserId(UUID userId);
}
