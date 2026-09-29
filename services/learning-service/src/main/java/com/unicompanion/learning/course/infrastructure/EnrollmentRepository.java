package com.unicompanion.learning.course.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, EnrollmentKey> {

    @Query("select e.id.courseId from EnrollmentEntity e where e.id.userId = :userId")
    List<UUID> findCourseIdsByUserId(@Param("userId") UUID userId);

    void deleteByIdCourseId(UUID courseId);
}
