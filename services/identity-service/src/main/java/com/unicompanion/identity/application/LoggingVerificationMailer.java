package com.unicompanion.identity.application;

import com.unicompanion.identity.config.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local stand-in for SMTP. The code is written to the log only when explicitly enabled.
 * The default and production setting leaves delivery unavailable.
 */
@Component
@Profile("!test")
public class LoggingVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationMailer.class);

    private final boolean logCodes;

    public LoggingVerificationMailer(IdentityProperties properties) {
        this.logCodes = Boolean.TRUE.equals(properties.identity().verification().logCodes());
    }

    @Override
    public boolean canSend() {
        return logCodes;
    }

    @Override
    public String delivery() {
        return "log";
    }

    @Override
    public void sendCode(String email, String code) {
        if (!logCodes) {
            throw new IllegalStateException("Verification mail is not configured.");
        }
        log.info("LOCAL ONLY university verification code for {} is {}", email, code);
    }
}
