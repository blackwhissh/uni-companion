package com.unicompanion.learning.matching.application;

import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.matching.domain.CollaborationIntent;
import com.unicompanion.learning.matching.infrastructure.MatchIntentEntity;
import com.unicompanion.learning.matching.infrastructure.MatchIntentKey;
import com.unicompanion.learning.matching.infrastructure.MatchIntentRepository;
import com.unicompanion.learning.matching.infrastructure.MatchOptInEntity;
import com.unicompanion.learning.matching.infrastructure.MatchOptInKey;
import com.unicompanion.learning.matching.infrastructure.MatchOptInRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final MatchOptInRepository optIns;
    private final MatchIntentRepository intents;

    public MatchingService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            MatchOptInRepository optIns,
            MatchIntentRepository intents
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.optIns = optIns;
        this.intents = intents;
    }

    @Transactional(readOnly = true)
    public MatchStatus status(UUID courseId, UUID userId) {
        requireEnrollment(courseId, userId);
        return toStatus(courseId, userId);
    }

    @Transactional
    public MatchStatus setOptIn(UUID courseId, UUID userId, boolean optedIn, String displayName) {
        requireEnrollment(courseId, userId);
        MatchOptInKey key = new MatchOptInKey(courseId, userId);
        if (optedIn) {
            String name = requireDisplayName(displayName);
            if (!optIns.existsById(key)) {
                optIns.save(MatchOptInEntity.create(key, Instant.now(), name));
                log.info("Match opt-in course={} user={}", courseId, userId);
            } else {
                optIns.findById(key).ifPresent(existing -> existing.rename(name));
            }
        } else if (optIns.existsById(key)) {
            optIns.deleteById(key);
            log.info("Match opt-out course={} user={}", courseId, userId);
        }
        return toStatus(courseId, userId);
    }

    @Transactional
    public Suggestions setIntents(UUID courseId, UUID userId, List<String> rawIntents, String displayName) {
        requireEnrollment(courseId, userId);
        MatchOptInKey key = new MatchOptInKey(courseId, userId);
        MatchOptInEntity optIn = optIns.findById(key).orElseThrow(MatchingService::forbidden);
        String name = requireDisplayName(displayName);
        optIn.rename(name);
        Set<CollaborationIntent> selected;
        try {
            selected = PartnerSuggestions.parse(rawIntents);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
        intents.deleteByIdCourseIdAndIdUserId(courseId, userId);
        intents.flush();
        for (CollaborationIntent intent : selected) {
            intents.save(MatchIntentEntity.create(new MatchIntentKey(courseId, userId, intent.name())));
        }
        log.info("Match intents course={} user={} count={}", courseId, userId, selected.size());
        return toSuggestions(courseId, userId);
    }

    @Transactional(readOnly = true)
    public Suggestions suggestions(UUID courseId, UUID userId) {
        requireEnrollment(courseId, userId);
        return toSuggestions(courseId, userId);
    }

    @Transactional
    public void clearOptIn(UUID courseId, UUID userId) {
        MatchOptInKey key = new MatchOptInKey(courseId, userId);
        if (optIns.existsById(key)) {
            optIns.deleteById(key);
            log.info("Match opt-in cleared course={} user={}", courseId, userId);
        }
    }

    private MatchStatus toStatus(UUID courseId, UUID userId) {
        boolean optedIn = optIns.existsById(new MatchOptInKey(courseId, userId));
        long activeCount = optIns.countByIdCourseId(courseId);
        return new MatchStatus(optedIn, activeCount);
    }

    private Suggestions toSuggestions(UUID courseId, UUID userId) {
        boolean discoverable = optIns.existsById(new MatchOptInKey(courseId, userId));
        if (!discoverable) {
            return new Suggestions(false, List.of(), List.of());
        }
        Map<UUID, Set<CollaborationIntent>> byUser = intents.findByIdCourseId(courseId).stream()
                .map(MatchIntentEntity::getId)
                .collect(Collectors.groupingBy(
                        MatchIntentKey::getUserId,
                        Collectors.mapping(MatchingService::intentOf, Collectors.toCollection(() -> EnumSet.noneOf(CollaborationIntent.class)))
                ));
        Set<CollaborationIntent> mine = byUser.getOrDefault(userId, Set.of());
        List<PartnerSuggestions.Candidate> others = new ArrayList<>();
        for (MatchOptInEntity optIn : optIns.findByIdCourseId(courseId)) {
            UUID otherId = optIn.getId().getUserId();
            if (otherId.equals(userId)) {
                continue;
            }
            others.add(new PartnerSuggestions.Candidate(
                    otherId,
                    optIn.getDisplayName(),
                    byUser.getOrDefault(otherId, Set.of())
            ));
        }
        return new Suggestions(true, ordered(mine), PartnerSuggestions.match(mine, others));
    }

    private static CollaborationIntent intentOf(MatchIntentKey key) {
        return CollaborationIntent.valueOf(key.getIntent());
    }

    private static List<CollaborationIntent> ordered(Set<CollaborationIntent> selected) {
        List<CollaborationIntent> values = new ArrayList<>();
        for (CollaborationIntent intent : CollaborationIntent.values()) {
            if (selected.contains(intent)) {
                values.add(intent);
            }
        }
        return values;
    }

    private static String requireDisplayName(String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty() || name.length() > 120) {
            throw badRequest("Add a display name before opting in.");
        }
        return name;
    }

    private void requireEnrollment(UUID courseId, UUID userId) {
        CourseEntity course = courses.findById(courseId).orElseThrow(MatchingService::notFound);
        if (!course.isPublished()) {
            throw notFound();
        }
        if (!enrollments.existsById(new EnrollmentKey(courseId, userId))) {
            log.warn("Match denied: user={} not enrolled in course={}", userId, courseId);
            throw forbidden();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public record MatchStatus(boolean optedIn, long activeCount) {
    }

    public record Suggestions(
            boolean discoverable,
            List<CollaborationIntent> intents,
            List<PartnerSuggestions.Match> suggestions
    ) {
    }
}
