package com.unicompanion.learning.admin;

import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentEntity;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.material.domain.ProcessingStatus;
import com.unicompanion.learning.material.infrastructure.MaterialEntity;
import com.unicompanion.learning.material.infrastructure.MaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminOverviewService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final MaterialRepository materials;

    public AdminOverviewService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            MaterialRepository materials
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.materials = materials;
    }

    @Transactional(readOnly = true)
    public List<CourseSnapshot> courses() {
        Map<UUID, Integer> enrollmentCounts = new HashMap<>();
        for (EnrollmentEntity enrollment : enrollments.findAll()) {
            enrollmentCounts.merge(enrollment.getCourseId(), 1, Integer::sum);
        }
        Map<UUID, int[]> materialCounts = new HashMap<>();
        for (MaterialEntity material : materials.findAll()) {
            int[] counts = materialCounts.computeIfAbsent(material.getCourseId(), ignored -> new int[3]);
            if (material.getProcessingStatus() == ProcessingStatus.FAILED) {
                counts[1]++;
            } else if (material.getVisibility() == Visibility.PUBLISHED
                    && material.getProcessingStatus() == ProcessingStatus.READY) {
                counts[0]++;
            } else {
                counts[2]++;
            }
        }
        return courses.findAllByOrderByCodeAsc().stream()
                .map(course -> snapshot(course, enrollmentCounts, materialCounts))
                .toList();
    }

    private static CourseSnapshot snapshot(
            CourseEntity course,
            Map<UUID, Integer> enrollmentCounts,
            Map<UUID, int[]> materialCounts
    ) {
        int[] counts = materialCounts.getOrDefault(course.getId(), new int[3]);
        return new CourseSnapshot(
                course.getId(),
                course.getTitle(),
                course.getCode(),
                course.getTerm(),
                course.getVisibility(),
                course.getOwnerId(),
                enrollmentCounts.getOrDefault(course.getId(), 0),
                counts[0],
                counts[1],
                counts[2]
        );
    }

    public record CourseSnapshot(
            UUID id,
            String title,
            String code,
            String term,
            Visibility visibility,
            UUID ownerId,
            int enrollmentCount,
            int publishedReadyMaterials,
            int failedMaterials,
            int otherMaterials
    ) {
    }
}
