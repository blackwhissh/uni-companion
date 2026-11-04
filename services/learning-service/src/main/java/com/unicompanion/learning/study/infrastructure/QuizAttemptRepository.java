package com.unicompanion.learning.study.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, UUID> {

    @Query("""
            select a from QuizAttemptEntity a, QuizEntity q
            where a.quizId = q.id
              and a.userId = :userId
              and q.courseId = :courseId
            """)
    List<QuizAttemptEntity> findForUserInCourse(@Param("userId") UUID userId, @Param("courseId") UUID courseId);
}
