package com.unicompanion.learning.course.infrastructure;

import com.unicompanion.learning.course.domain.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<CourseEntity, UUID> {

    List<CourseEntity> findByVisibilityOrderByCodeAsc(Visibility visibility);

    List<CourseEntity> findAllByOrderByCodeAsc();
}
