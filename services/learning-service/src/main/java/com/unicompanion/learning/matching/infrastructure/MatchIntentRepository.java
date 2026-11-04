package com.unicompanion.learning.matching.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchIntentRepository extends JpaRepository<MatchIntentEntity, MatchIntentKey> {

    List<MatchIntentEntity> findByIdCourseId(UUID courseId);

    void deleteByIdCourseIdAndIdUserId(UUID courseId, UUID userId);
}
