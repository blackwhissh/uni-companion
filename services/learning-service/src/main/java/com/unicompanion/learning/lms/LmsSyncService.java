package com.unicompanion.learning.lms;

import com.unicompanion.learning.config.AuditService;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import com.unicompanion.learning.course.infrastructure.CourseRepository;
import com.unicompanion.learning.ingestion.PdfTextExtraction;
import com.unicompanion.learning.material.application.MaterialService;
import com.unicompanion.learning.material.infrastructure.MaterialEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LmsSyncService {

    private static final Logger log = LoggerFactory.getLogger(LmsSyncService.class);

    private final CourseRepository courses;
    private final CourseLmsLinkRepository links;
    private final MaterialService materials;
    private final PdfTextExtraction extraction;
    private final AuditService audit;
    private final Map<LmsProvider, LmsClient> clients;

    public LmsSyncService(
            CourseRepository courses,
            CourseLmsLinkRepository links,
            MaterialService materials,
            PdfTextExtraction extraction,
            AuditService audit,
            List<LmsClient> clients
    ) {
        this.courses = courses;
        this.links = links;
        this.materials = materials;
        this.extraction = extraction;
        this.audit = audit;
        this.clients = clients.stream().collect(Collectors.toMap(LmsClient::provider, c -> c, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public CourseLmsLinkEntity getLink(UUID courseId, UUID userId, boolean platformAdmin) {
        requireManage(courseId, userId, platformAdmin);
        return links.findById(courseId).orElseThrow(LmsSyncService::notFound);
    }

    @Transactional
    public CourseLmsLinkEntity upsertLink(
            UUID courseId,
            UUID userId,
            boolean platformAdmin,
            LmsProvider provider,
            String externalCourseId,
            boolean enabled,
            String correlationId
    ) {
        CourseEntity course = requireManage(courseId, userId, platformAdmin);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an LMS provider.");
        }
        if (externalCourseId == null || externalCourseId.isBlank() || externalCourseId.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the external LMS course id.");
        }
        if (!clients.containsKey(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LMS provider is not configured: " + provider);
        }
        Instant now = Instant.now();
        CourseLmsLinkEntity link = links.findById(courseId).orElse(null);
        if (link == null) {
            link = CourseLmsLinkEntity.create(courseId, provider, externalCourseId, now);
            if (!enabled) {
                link.update(provider, externalCourseId, false, now);
            }
        } else {
            link.update(provider, externalCourseId, enabled, now);
        }
        CourseLmsLinkEntity saved = links.save(link);
        audit.record(
                userId,
                "LMS_LINK_UPSERT",
                "course",
                courseId.toString(),
                Map.of(
                        "provider", provider.name(),
                        "externalCourseId", saved.getExternalCourseId(),
                        "enabled", saved.isEnabled(),
                        "code", course.getCode()
                ),
                correlationId
        );
        log.info(
                "LMS link upserted course={} provider={} external={} enabled={} by={}",
                courseId,
                provider,
                saved.getExternalCourseId(),
                saved.isEnabled(),
                userId
        );
        return saved;
    }

    /**
     * Not {@code @Transactional}: each upsert commits on its own, then ingest runs
     * (same pattern as manual upload) so async indexing sees committed rows.
     */
    public LmsSyncResult sync(UUID courseId, UUID userId, boolean platformAdmin, String correlationId) {
        CourseEntity course = requireManage(courseId, userId, platformAdmin);
        CourseLmsLinkEntity link = links.findById(courseId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link an LMS course before syncing."));
        if (!link.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LMS sync is disabled for this course.");
        }
        LmsClient client = clients.get(link.getProvider());
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LMS provider is not configured: " + link.getProvider());
        }

        Instant now = Instant.now();
        List<LmsRemoteFile> remoteFiles;
        try {
            remoteFiles = client.listFiles(link.getExternalCourseId());
        } catch (RuntimeException ex) {
            link.recordSync("FAILED", ex.getMessage(), now);
            links.save(link);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not list files from the LMS.", ex);
        }

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        List<String> materialIds = new ArrayList<>();

        for (LmsRemoteFile remote : remoteFiles) {
            if (!isPdf(remote)) {
                skipped++;
                continue;
            }
            byte[] bytes;
            try {
                bytes = client.download(link.getExternalCourseId(), remote.externalFileId());
            } catch (RuntimeException ex) {
                link.recordSync("FAILED", "Download failed for " + remote.fileName() + ": " + ex.getMessage(), now);
                links.save(link);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not download a file from the LMS.", ex);
            }
            MaterialService.LmsUpsertOutcome outcome = materials.upsertFromLms(
                    userId,
                    courseId,
                    link.getProvider().name(),
                    remote.externalFileId(),
                    remote.checksum(),
                    remote.title(),
                    remote.fileName(),
                    remote.contentType() == null ? "application/pdf" : remote.contentType(),
                    bytes,
                    platformAdmin
            );
            switch (outcome.action()) {
                case IMPORTED -> {
                    imported++;
                    materialIds.add(outcome.material().getId().toString());
                    extraction.schedule(outcome.material().getId());
                }
                case UPDATED -> {
                    updated++;
                    materialIds.add(outcome.material().getId().toString());
                    extraction.schedule(outcome.material().getId());
                }
                case SKIPPED -> skipped++;
            }
        }

        String message = "Imported %d, updated %d, skipped %d.".formatted(imported, updated, skipped);
        link.recordSync("OK", message, now);
        links.save(link);
        audit.record(
                userId,
                "LMS_SYNC",
                "course",
                courseId.toString(),
                Map.of(
                        "provider", link.getProvider().name(),
                        "externalCourseId", link.getExternalCourseId(),
                        "imported", imported,
                        "updated", updated,
                        "skipped", skipped,
                        "code", course.getCode()
                ),
                correlationId
        );
        log.info(
                "LMS sync course={} provider={} external={} imported={} updated={} skipped={} by={}",
                courseId,
                link.getProvider(),
                link.getExternalCourseId(),
                imported,
                updated,
                skipped,
                userId
        );
        return new LmsSyncResult(
                courseId,
                link.getProvider(),
                link.getExternalCourseId(),
                "OK",
                imported,
                updated,
                skipped,
                now,
                List.copyOf(materialIds),
                message
        );
    }

    private CourseEntity requireManage(UUID courseId, UUID userId, boolean platformAdmin) {
        CourseEntity course = courses.findById(courseId).orElseThrow(LmsSyncService::notFound);
        boolean owner = course.getOwnerId().equals(userId);
        if (!owner && !platformAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return course;
    }

    private static boolean isPdf(LmsRemoteFile remote) {
        String name = remote.fileName() == null ? "" : remote.fileName().toLowerCase(Locale.ROOT);
        String type = remote.contentType() == null ? "" : remote.contentType().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || type.startsWith("application/pdf");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
