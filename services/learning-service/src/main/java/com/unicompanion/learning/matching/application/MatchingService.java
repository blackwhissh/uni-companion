package com.unicompanion.learning.matching.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.course.infrastructure.EnrollmentKey;
import com.unicompanion.learning.course.infrastructure.EnrollmentRepository;
import com.unicompanion.learning.matching.domain.CollaborationIntent;
import com.unicompanion.learning.matching.domain.ContactState;
import com.unicompanion.learning.matching.domain.ContactStatus;
import com.unicompanion.learning.matching.infrastructure.ContactRequestEntity;
import com.unicompanion.learning.matching.infrastructure.ContactRequestRepository;
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
import java.util.HashMap;
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
    private final ContactRequestRepository contacts;

    public MatchingService(
            CourseRepository courses,
            EnrollmentRepository enrollments,
            MatchOptInRepository optIns,
            MatchIntentRepository intents,
            ContactRequestRepository contacts
    ) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.optIns = optIns;
        this.intents = intents;
        this.contacts = contacts;
    }

    @Transactional(readOnly = true)
    public MatchStatus status(UUID courseId, UUID userId) {
        requireEnrollment(courseId, userId);
        return toStatus(courseId, userId);
    }

    @Transactional
    public MatchStatus setOptIn(UUID courseId, UUID userId, boolean optedIn, String displayName, String email) {
        requireEnrollment(courseId, userId);
        MatchOptInKey key = new MatchOptInKey(courseId, userId);
        if (optedIn) {
            String name = requireDisplayName(displayName);
            String contactEmail = normalizeContact(email);
            if (!optIns.existsById(key)) {
                optIns.save(MatchOptInEntity.create(key, Instant.now(), name, contactEmail));
                log.info("Match opt-in course={} user={}", courseId, userId);
            } else {
                optIns.findById(key).ifPresent(existing -> existing.updateProfile(name, contactEmail));
            }
        } else if (optIns.existsById(key)) {
            dropPending(courseId, userId);
            optIns.deleteById(key);
            log.info("Match opt-out course={} user={}", courseId, userId);
        }
        return toStatus(courseId, userId);
    }

    @Transactional
    public Suggestions setIntents(UUID courseId, UUID userId, List<String> rawIntents, String displayName, String email) {
        requireEnrollment(courseId, userId);
        MatchOptInKey key = new MatchOptInKey(courseId, userId);
        MatchOptInEntity optIn = optIns.findById(key).orElseThrow(MatchingService::forbidden);
        optIn.updateProfile(requireDisplayName(displayName), normalizeContact(email));
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
    public Suggestions requestContact(UUID courseId, UUID requesterId, UUID recipientId) {
        requireEnrollment(courseId, requesterId);
        if (requesterId.equals(recipientId)) {
            throw badRequest("Choose a classmate.");
        }
        MatchOptInEntity requester = optIns.findById(new MatchOptInKey(courseId, requesterId)).orElseThrow(MatchingService::forbidden);
        MatchOptInEntity recipient = optIns.findById(new MatchOptInKey(courseId, recipientId))
                .orElseThrow(() -> badRequest("That classmate is not available."));
        if (requester.getContactEmail().isBlank() || recipient.getContactEmail().isBlank()) {
            throw badRequest("Save your study-partner profile again before requesting contact.");
        }
        if (!sharesIntent(courseId, requesterId, recipientId)) {
            throw badRequest("You can only request a classmate who shares an intent.");
        }
        ContactRequestEntity reverse = contacts
                .findByCourseIdAndRequesterIdAndRecipientId(courseId, recipientId, requesterId)
                .orElse(null);
        if (reverse != null && reverse.getStatus() == ContactStatus.PENDING) {
            throw conflict("They already asked you. Accept or decline that request.");
        }
        if (reverse != null && reverse.getStatus() == ContactStatus.ACCEPTED) {
            return toSuggestions(courseId, requesterId);
        }
        ContactRequestEntity forward = contacts
                .findByCourseIdAndRequesterIdAndRecipientId(courseId, requesterId, recipientId)
                .orElse(null);
        if (forward == null) {
            contacts.save(ContactRequestEntity.pending(
                    UUID.randomUUID(),
                    courseId,
                    requesterId,
                    recipientId,
                    requester.getDisplayName(),
                    recipient.getDisplayName(),
                    Instant.now()
            ));
            log.info("Contact requested course={} from={} to={}", courseId, requesterId, recipientId);
        } else if (forward.getStatus() == ContactStatus.DECLINED) {
            forward.reopen(requester.getDisplayName(), recipient.getDisplayName());
            log.info("Contact request reopened course={} from={} to={}", courseId, requesterId, recipientId);
        }
        return toSuggestions(courseId, requesterId);
    }

    @Transactional
    public Suggestions acceptContact(UUID courseId, UUID userId, UUID requestId) {
        requireEnrollment(courseId, userId);
        ContactRequestEntity request = requireRequest(courseId, requestId);
        if (!request.getRecipientId().equals(userId)) {
            throw forbidden();
        }
        if (request.getStatus() == ContactStatus.DECLINED) {
            throw conflict("This request was already declined.");
        }
        if (request.getStatus() == ContactStatus.PENDING) {
            MatchOptInEntity requester = optIns.findById(new MatchOptInKey(courseId, request.getRequesterId()))
                    .orElseThrow(() -> conflict("That classmate is no longer available."));
            MatchOptInEntity recipient = optIns.findById(new MatchOptInKey(courseId, userId))
                    .orElseThrow(MatchingService::forbidden);
            if (requester.getContactEmail().isBlank() || recipient.getContactEmail().isBlank()) {
                throw badRequest("Save your study-partner profile again before accepting contact.");
            }
            request.accept(
                    requester.getDisplayName(),
                    recipient.getDisplayName(),
                    requester.getContactEmail(),
                    recipient.getContactEmail(),
                    Instant.now()
            );
            log.info("Contact accepted course={} request={}", courseId, requestId);
        }
        return toSuggestions(courseId, userId);
    }

    @Transactional
    public Suggestions declineContact(UUID courseId, UUID userId, UUID requestId) {
        requireEnrollment(courseId, userId);
        ContactRequestEntity request = requireRequest(courseId, requestId);
        if (!request.getRecipientId().equals(userId)) {
            throw forbidden();
        }
        if (request.getStatus() == ContactStatus.ACCEPTED) {
            throw conflict("This request was already accepted.");
        }
        if (request.getStatus() == ContactStatus.PENDING) {
            request.decline(Instant.now());
            log.info("Contact declined course={} request={}", courseId, requestId);
        }
        return toSuggestions(courseId, userId);
    }

    @Transactional
    public void clearOptIn(UUID courseId, UUID userId) {
        MatchOptInKey key = new MatchOptInKey(courseId, userId);
        if (optIns.existsById(key)) {
            dropPending(courseId, userId);
            optIns.deleteById(key);
            log.info("Match opt-in cleared course={} user={}", courseId, userId);
        }
    }

    private void dropPending(UUID courseId, UUID userId) {
        contacts.deleteByCourseAndStatus(courseId, userId, ContactStatus.PENDING);
    }

    private MatchStatus toStatus(UUID courseId, UUID userId) {
        boolean optedIn = optIns.existsById(new MatchOptInKey(courseId, userId));
        long activeCount = optIns.countByIdCourseId(courseId);
        return new MatchStatus(optedIn, activeCount);
    }

    private Suggestions toSuggestions(UUID courseId, UUID userId) {
        List<ContactRequestEntity> involved = contacts.findInvolving(courseId, userId);
        boolean discoverable = optIns.existsById(new MatchOptInKey(courseId, userId));
        if (!discoverable) {
            return new Suggestions(false, List.of(), List.of(), contactViews(userId, involved, true));
        }
        Map<UUID, Set<CollaborationIntent>> byUser = intents.findByIdCourseId(courseId).stream()
                .map(MatchIntentEntity::getId)
                .collect(Collectors.groupingBy(
                        MatchIntentKey::getUserId,
                        Collectors.mapping(MatchingService::intentOf, Collectors.toCollection(() -> EnumSet.noneOf(CollaborationIntent.class)))
                ));
        Set<CollaborationIntent> mine = byUser.getOrDefault(userId, Set.of());
        Map<UUID, String> names = new HashMap<>();
        List<PartnerSuggestions.Candidate> others = new ArrayList<>();
        for (MatchOptInEntity optIn : optIns.findByIdCourseId(courseId)) {
            UUID otherId = optIn.getId().getUserId();
            names.put(otherId, optIn.getDisplayName());
            if (otherId.equals(userId)) {
                continue;
            }
            others.add(new PartnerSuggestions.Candidate(
                    otherId,
                    optIn.getDisplayName(),
                    byUser.getOrDefault(otherId, Set.of())
            ));
        }
        Map<UUID, ContactRequestEntity> byOther = indexByOther(userId, involved);
        List<Suggestion> suggestions = new ArrayList<>();
        for (PartnerSuggestions.Match match : PartnerSuggestions.match(mine, others)) {
            suggestions.add(toSuggestion(userId, match, byOther.get(match.userId())));
        }
        return new Suggestions(true, ordered(mine), suggestions, contactViews(userId, involved, false, names));
    }

    private Suggestion toSuggestion(UUID viewerId, PartnerSuggestions.Match match, ContactRequestEntity request) {
        if (request == null || request.getStatus() == ContactStatus.DECLINED) {
            return new Suggestion(match.userId(), match.displayName(), match.sharedIntents(), ContactState.NONE, null, null);
        }
        String email = ContactExchange.emailForViewer(
                request.getStatus(),
                viewerId,
                request.getRequesterId(),
                request.getRecipientId(),
                request.getRequesterEmail(),
                request.getRecipientEmail()
        );
        ContactState state = request.getStatus() == ContactStatus.ACCEPTED
                ? ContactState.ACCEPTED
                : request.getRequesterId().equals(viewerId) ? ContactState.OUTGOING : ContactState.INCOMING;
        return new Suggestion(match.userId(), match.displayName(), match.sharedIntents(), state, request.getId(), email);
    }

    private List<ContactView> contactViews(UUID viewerId, List<ContactRequestEntity> involved, boolean acceptedOnly) {
        return contactViews(viewerId, involved, acceptedOnly, Map.of());
    }

    private List<ContactView> contactViews(
            UUID viewerId,
            List<ContactRequestEntity> involved,
            boolean acceptedOnly,
            Map<UUID, String> liveNames
    ) {
        List<ContactView> views = new ArrayList<>();
        for (ContactRequestEntity request : involved) {
            if (acceptedOnly && request.getStatus() != ContactStatus.ACCEPTED) {
                continue;
            }
            boolean viewerIsRequester = request.getRequesterId().equals(viewerId);
            if (request.getStatus() == ContactStatus.DECLINED && !viewerIsRequester) {
                continue;
            }
            UUID otherId = viewerIsRequester ? request.getRecipientId() : request.getRequesterId();
            String storedName = viewerIsRequester ? request.getRecipientName() : request.getRequesterName();
            String name = liveNames.getOrDefault(otherId, storedName);
            String email = ContactExchange.emailForViewer(
                    request.getStatus(),
                    viewerId,
                    request.getRequesterId(),
                    request.getRecipientId(),
                    request.getRequesterEmail(),
                    request.getRecipientEmail()
            );
            views.add(new ContactView(
                    request.getId(),
                    otherId,
                    name,
                    viewerIsRequester ? "OUTGOING" : "INCOMING",
                    request.getStatus(),
                    email
            ));
        }
        views.sort((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()));
        return views;
    }

    private static Map<UUID, ContactRequestEntity> indexByOther(UUID viewerId, List<ContactRequestEntity> involved) {
        Map<UUID, ContactRequestEntity> byOther = new HashMap<>();
        for (ContactRequestEntity request : involved) {
            UUID otherId = request.getRequesterId().equals(viewerId) ? request.getRecipientId() : request.getRequesterId();
            byOther.merge(otherId, request, MatchingService::prefer);
        }
        return byOther;
    }

    private static ContactRequestEntity prefer(ContactRequestEntity current, ContactRequestEntity candidate) {
        if (current.getStatus() == ContactStatus.ACCEPTED) {
            return current;
        }
        if (candidate.getStatus() == ContactStatus.ACCEPTED) {
            return candidate;
        }
        if (current.getStatus() == ContactStatus.PENDING) {
            return current;
        }
        return candidate;
    }

    private boolean sharesIntent(UUID courseId, UUID leftId, UUID rightId) {
        Set<CollaborationIntent> left = EnumSet.noneOf(CollaborationIntent.class);
        Set<CollaborationIntent> right = EnumSet.noneOf(CollaborationIntent.class);
        for (MatchIntentEntity row : intents.findByIdCourseId(courseId)) {
            CollaborationIntent intent = intentOf(row.getId());
            if (row.getId().getUserId().equals(leftId)) {
                left.add(intent);
            } else if (row.getId().getUserId().equals(rightId)) {
                right.add(intent);
            }
        }
        for (CollaborationIntent intent : left) {
            if (right.contains(intent)) {
                return true;
            }
        }
        return false;
    }

    private ContactRequestEntity requireRequest(UUID courseId, UUID requestId) {
        ContactRequestEntity request = contacts.findById(requestId).orElseThrow(MatchingService::notFound);
        if (!request.getCourseId().equals(courseId)) {
            throw notFound();
        }
        return request;
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

    private static String normalizeContact(String email) {
        if (email == null) {
            return "";
        }
        String value = email.trim();
        return value.length() > 320 ? "" : value;
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

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    public record MatchStatus(boolean optedIn, long activeCount) {
    }

    public record Suggestions(
            boolean discoverable,
            List<CollaborationIntent> intents,
            List<Suggestion> suggestions,
            List<ContactView> requests
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Suggestion(
            UUID userId,
            String displayName,
            List<CollaborationIntent> sharedIntents,
            ContactState contact,
            UUID requestId,
            String email
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContactView(
            UUID id,
            UUID userId,
            String displayName,
            String direction,
            ContactStatus status,
            String email
    ) {
    }
}
