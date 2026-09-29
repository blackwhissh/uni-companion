package com.unicompanion.learning.material.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.ingestion.PdfTextExtraction;
import com.unicompanion.learning.material.application.MaterialFile;
import com.unicompanion.learning.material.application.MaterialService;
import com.unicompanion.learning.material.domain.ProcessingStatus;
import com.unicompanion.learning.material.infrastructure.MaterialEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class MaterialController {

    private final MaterialService materials;
    private final PdfTextExtraction extraction;

    public MaterialController(MaterialService materials, PdfTextExtraction extraction) {
        this.materials = materials;
        this.extraction = extraction;
    }

    @PostMapping(path = "/api/courses/{courseId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialResponse upload(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        MaterialEntity accepted = materials.accept(
                userId(jwt),
                courseId,
                title,
                file.getOriginalFilename(),
                file.getContentType(),
                bytes(file),
                isPlatformAdmin(jwt)
        );
        extraction.extract(accepted.getId());
        return MaterialResponse.from(accepted);
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
            @Valid @RequestBody VisibilityRequest request
    ) {
        return MaterialResponse.from(materials.changeVisibility(id, userId(jwt), request.visibility(), isPlatformAdmin(jwt)));
    }

    @DeleteMapping("/api/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        materials.delete(id, userId(jwt), isPlatformAdmin(jwt));
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
            String failureReason
    ) {
        static MaterialResponse from(MaterialEntity material) {
            return new MaterialResponse(
                    material.getId(),
                    material.getCourseId(),
                    material.getTitle(),
                    material.getFileName(),
                    material.getVisibility(),
                    material.getProcessingStatus(),
                    material.getFailureReason()
            );
        }
    }
}
