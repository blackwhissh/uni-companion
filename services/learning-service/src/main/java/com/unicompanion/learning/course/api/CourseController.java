package com.unicompanion.learning.course.api;

import com.unicompanion.learning.course.application.CourseService;
import com.unicompanion.learning.course.domain.Visibility;
import com.unicompanion.learning.course.infrastructure.CourseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCourseRequest request) {
        return CourseResponse.from(courses.create(userId(jwt), request.title(), request.code(), request.term()), false, true);
    }

    @GetMapping
    public List<CourseResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return courses.listFor(userId(jwt), isCourseStaff(jwt)).stream().map(CourseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CourseResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return CourseResponse.from(courses.getVisible(id, userId(jwt), isCourseStaff(jwt)));
    }

    @PatchMapping("/{id}/visibility")
    public CourseResponse changeVisibility(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VisibilityRequest request
    ) {
        CourseService.CourseDetails updated = courses.changeVisibility(
                id,
                request.visibility(),
                userId(jwt),
                isPlatformAdmin(jwt)
        );
        return CourseResponse.from(updated);
    }

    @PostMapping("/{id}/enroll")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        CourseService.EnrollmentResult result = courses.enroll(id, userId(jwt));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new EnrollmentResponse(result.courseId()));
    }

    @DeleteMapping("/{id}/enroll")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenroll(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        courses.unenroll(id, userId(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        courses.delete(id, userId(jwt), isPlatformAdmin(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean isCourseStaff(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains("COURSE_ADMIN") || roles.contains("ADMIN"));
    }

    private static boolean isPlatformAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }

    public record CreateCourseRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 32) String term
    ) {
    }

    public record VisibilityRequest(@NotNull Visibility visibility) {
    }

    public record CourseResponse(
            UUID id,
            String title,
            String code,
            String term,
            Visibility visibility,
            boolean enrolled,
            boolean owned
    ) {
        static CourseResponse from(CourseEntity course, boolean enrolled, boolean owned) {
            return new CourseResponse(
                    course.getId(),
                    course.getTitle(),
                    course.getCode(),
                    course.getTerm(),
                    course.getVisibility(),
                    enrolled,
                    owned
            );
        }

        static CourseResponse from(CourseService.CourseDetails details) {
            return from(details.course(), details.enrolled(), details.owned());
        }
    }

    public record EnrollmentResponse(UUID courseId) {
    }
}
