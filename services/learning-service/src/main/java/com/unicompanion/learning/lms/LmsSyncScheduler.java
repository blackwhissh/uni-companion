package com.unicompanion.learning.lms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically syncs every enabled course LMS link. Actor for audit is the course owner.
 */
@Component
@ConditionalOnProperty(prefix = "unicompanion.lms.sync", name = "scheduled-enabled", havingValue = "true")
public class LmsSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(LmsSyncScheduler.class);

    private final LmsSyncService sync;

    public LmsSyncScheduler(LmsSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${unicompanion.lms.sync.cron:0 0 */6 * * *}")
    public void syncEnabledCourses() {
        int ok = 0;
        int failed = 0;
        for (CourseLmsLinkEntity link : sync.listEnabledLinks()) {
            try {
                LmsSyncResult result = sync.syncScheduled(link.getCourseId());
                ok++;
                log.info(
                        "Scheduled LMS sync ok course={} imported={} updated={} skipped={}",
                        result.courseId(),
                        result.imported(),
                        result.updated(),
                        result.skipped()
                );
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Scheduled LMS sync failed course={}", link.getCourseId(), ex);
            }
        }
        log.info("Scheduled LMS sync finished ok={} failed={}", ok, failed);
    }
}
