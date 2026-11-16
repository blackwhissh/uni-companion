package com.unicompanion.learning.material.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unicompanion.learning.config.AuditService;
import com.unicompanion.learning.config.CorrelationIdFilter;
import com.unicompanion.learning.config.IdempotencyService;
import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.ingestion.PdfTextExtraction;
import com.unicompanion.learning.material.application.MaterialFile;
import com.unicompanion.learning.material.application.MaterialService;
import com.unicompanion.learning.material.domain.ProcessingStatus;
import com.unicompanion.learning.material.infrastructure.MaterialEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class MaterialController {

    private final MaterialService materials;
    private final PdfTextExtraction extraction;
    private final AuditService audit;
    private final IdempotencyService idempotency;
    private final JsonMapper json;

    public MaterialController(
            MaterialService materials,
            PdfTextExtraction extraction,
            AuditService audit,
            IdempotencyService idempotency,
            JsonMapper json
    ) {
        this.materials = materials;
        this.extraction = extraction;
        this.audit = audit;
        this.idempotency = idempotency;
        this.json = json;
    }

    @PostMapping(path = "/api/courses/{courseId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialResponse> upload(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        UUID uid = userId(jwt);
        byte[] content = bytes(file);
        String route = "POST /api/courses/" + courseId + "/materials";
        String fingerprint = IdempotencyService.fingerprint(
                route,
                String.valueOf(title),
                file.getOriginalFilename(),
                String.valueOf(content.length),
                IdempotencyService.fingerprintBytes(content)
        );
        Optional<IdempotencyService.CachedResponse> cached = idempotency.begin(uid, idempotencyKey, route, fingerprint);
        if (cached.isPresent()) {
            return cachedResponse(cached.get(), MaterialResponse.class);
        }
        try {
            MaterialEntity accepted = materials.accept(
                    uid,
                    courseId,
                    title,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    content,
                    isPlatformAdmin(jwt)
            );
            extraction.schedule(accepted.getId());
            MaterialResponse body = MaterialResponse.from(materials.get(accepted.getId()));
            idempotency.complete(uid, idempotencyKey, HttpStatus.CREATED.value(), writeJson(body));
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (RuntimeException ex) {
            idempotency.fail(uid, idempotencyKey);
            throw ex;
        }
    }

    @PostMapping(path = "/api/materials/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialResponse replace(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        UUID uid = userId(jwt);
        byte[] content = bytes(file);
        String route = "POST /api/materials/" + id + "/file";
        String fingerprint = IdempotencyService.fingerprint(
                route,
                String.valueOf(title),
                file.getOriginalFilename(),
                String.valueOf(content.length),
                IdempotencyService.fingerprintBytes(content)
        );
        Optional<IdempotencyService.CachedResponse> cached = idempotency.begin(uid, idempotencyKey, route, fingerprint);
        if (cached.isPresent()) {
            return readJson(cached.get().body(), MaterialResponse.class);
        }
        try {
            materials.replace(
                    id,
                    uid,
                    title,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    content,
                    isPlatformAdmin(jwt)
            );
            extraction.schedule(id);
            MaterialResponse body = MaterialResponse.from(materials.get(id));
            idempotency.complete(uid, idempotencyKey, HttpStatus.OK.value(), writeJson(body));
            return body;
        } catch (RuntimeException ex) {
            idempotency.fail(uid, idempotencyKey);
            throw ex;
        }
    }

    @PostMapping("/api/materials/{id}/retry")
    public MaterialResponse retry(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        materials.prepareRetry(id, userId(jwt), isPlatformAdmin(jwt));
        extraction.schedule(id);
        return MaterialResponse.from(materials.get(id));
    }

    @GetMapping("/api/courses/{courseId}/materials")
    public List<MaterialResponse> list(@PathVariable UUID courseId, @AuthenticationPrincipal Jwt jwt) {
        return materials.list(courseId, userId(jwt), isAdmin(jwt)).stream().map(MaterialResponse::from).toList();
    }

    @GetMapping("/api/materials/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        MaterialFile file = materials.download(id, userId(jwt), isAdmin(jwt));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.bytes());
    }

    @PatchMapping("/api/materials/{id}/visibility")
    public MaterialResponse changeVisibility(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VisibilityRequest request,
            HttpServletRequest httpRequest
    ) {
        MaterialEntity updated = materials.changeVisibility(id, userId(jwt), request.visibility(), isPlatformAdmin(jwt));
        audit.record(
                userId(jwt),
                "MATERIAL_VISIBILITY_CHANGED",
                "material",
                id.toString(),
                Map.of("visibility", request.visibility().name()),
                CorrelationIdFilter.correlationId(httpRequest)
        );
        return MaterialResponse.from(updated);
    }

    @DeleteMapping("/api/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        materials.delete(id, userId(jwt), isPlatformAdmin(jwt));
        audit.record(
                userId(jwt),
                "MATERIAL_DELETED",
                "material",
                id.toString(),
                Map.of(),
                CorrelationIdFilter.correlationId(httpRequest)
        );
    }

    private <T> ResponseEntity<T> cachedResponse(IdempotencyService.CachedResponse cached, Class<T> type) {
        return ResponseEntity.status(cached.httpStatus()).body(readJson(cached.body(), type));
    }

    private String writeJson(Object value) {
        return json.writeValueAsString(value);
    }

    private <T> T readJson(String body, Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not replay idempotent response.", ex);
        }
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains("COURSE_ADMIN") || roles.contains("ADMIN"));
    }

    private static boolean isPlatformAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }

    public record VisibilityRequest(@NotNull Visibility visibility) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MaterialResponse(
            UUID id,
            UUID courseId,
            String title,
            String fileName,
            Visibility visibility,
            ProcessingStatus processingStatus,
            String failureReason,
            String sourceProvider,
            String sourceExternalId
    ) {
        static MaterialResponse from(MaterialEntity material) {
            return new MaterialResponse(
                    material.getId(),
                    material.getCourseId(),
                    material.getTitle(),
                    material.getFileName(),
                    material.getVisibility(),
                    material.getProcessingStatus(),
                    material.getFailureReason(),
                    material.getSourceProvider(),
                    material.getSourceExternalId()
            );
        }
    }
}
