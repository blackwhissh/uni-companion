package com.unicompanion.learning.matching.infrastructure;

import com.unicompanion.learning.matching.domain.ContactStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRequestRepository extends JpaRepository<ContactRequestEntity, UUID> {

    Optional<ContactRequestEntity> findByCourseIdAndRequesterIdAndRecipientId(UUID courseId, UUID requesterId, UUID recipientId);

    @Query("""
            select request from ContactRequestEntity request
            where request.courseId = :courseId
              and (request.requesterId = :userId or request.recipientId = :userId)
            """)
    List<ContactRequestEntity> findInvolving(@Param("courseId") UUID courseId, @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ContactRequestEntity request
            where request.courseId = :courseId
              and request.status = :status
              and (request.requesterId = :userId or request.recipientId = :userId)
            """)
    void deleteByCourseAndStatus(
            @Param("courseId") UUID courseId,
            @Param("userId") UUID userId,
            @Param("status") ContactStatus status
    );
}
