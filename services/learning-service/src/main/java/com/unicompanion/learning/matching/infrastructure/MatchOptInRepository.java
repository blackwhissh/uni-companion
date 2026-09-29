package com.unicompanion.learning.matching.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchOptInRepository extends JpaRepository<MatchOptInEntity, MatchOptInKey> {

    long countByIdCourseId(UUID courseId);
}
