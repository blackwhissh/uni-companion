package com.unicompanion.learning.lms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseLmsLinkRepository extends JpaRepository<CourseLmsLinkEntity, UUID> {

    List<CourseLmsLinkEntity> findByEnabledTrue();
}
