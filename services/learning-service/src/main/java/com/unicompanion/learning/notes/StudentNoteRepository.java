package com.unicompanion.learning.notes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentNoteRepository extends JpaRepository<StudentNoteEntity, UUID> {

    List<StudentNoteEntity> findByCourseIdAndUserIdOrderByUpdatedAtDesc(UUID courseId, UUID userId);

    Optional<StudentNoteEntity> findByIdAndCourseIdAndUserId(UUID id, UUID courseId, UUID userId);

    void deleteByCourseIdAndUserId(UUID courseId, UUID userId);
}
