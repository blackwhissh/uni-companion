package com.unicompanion.learning.matching.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchOptInRepository extends JpaRepository<MatchOptInEntity, MatchOptInKey> {

    long countByIdCourseId(UUID courseId);

    List<MatchOptInEntity> findByIdCourseId(UUID courseId);
}
