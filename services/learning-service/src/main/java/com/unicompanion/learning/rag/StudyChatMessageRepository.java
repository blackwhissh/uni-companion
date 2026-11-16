package com.unicompanion.learning.rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StudyChatMessageRepository extends JpaRepository<StudyChatMessageEntity, UUID> {

    List<StudyChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    long countBySessionId(UUID sessionId);
}
