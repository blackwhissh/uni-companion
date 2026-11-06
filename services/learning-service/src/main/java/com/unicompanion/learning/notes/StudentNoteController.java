package com.unicompanion.learning.notes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notes/courses/{courseId}")
public class StudentNoteController {

    private final StudentNoteService notes;

    public StudentNoteController(StudentNoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<StudentNoteService.NoteView> list(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return notes.list(courseId, userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentNoteService.NoteView create(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NoteRequest request
    ) {
        return notes.create(courseId, userId(jwt), request.title(), request.body());
    }

    @PutMapping("/{noteId}")
    public StudentNoteService.NoteView update(
            @PathVariable UUID courseId,
            @PathVariable UUID noteId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NoteRequest request
    ) {
        return notes.update(courseId, noteId, userId(jwt), request.title(), request.body());
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID courseId,
            @PathVariable UUID noteId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        notes.delete(courseId, noteId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record NoteRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 8000) String body
    ) {
    }
}
