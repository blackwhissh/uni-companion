package com.unicompanion.learning.lms;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unicompanion.lms.sync")
public record LmsSyncProperties(
        boolean scheduledEnabled,
        String cron
) {
    public LmsSyncProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 0 */6 * * *";
        }
    }
}
