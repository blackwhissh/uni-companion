package com.unicompanion.learning.lms;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LmsSyncResult(
        UUID courseId,
        LmsProvider provider,
        String externalCourseId,
        String status,
        int imported,
        int updated,
        int skipped,
        Instant syncedAt,
        List<String> materialIds,
        String message
) {
}
