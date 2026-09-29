package com.unicompanion.learning.material.infrastructure;

import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.material.domain.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<MaterialEntity, UUID> {

    List<MaterialEntity> findByCourseIdOrderByTitleAsc(UUID courseId);

    List<MaterialEntity> findByCourseIdAndVisibilityAndProcessingStatusOrderByTitleAsc(
            UUID courseId,
            Visibility visibility,
            ProcessingStatus processingStatus
    );
}
