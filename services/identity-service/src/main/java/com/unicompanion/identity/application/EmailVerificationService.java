package com.unicompanion.identity.application;

import com.unicompanion.identity.config.IdentityProperties;
import com.unicompanion.identity.domain.IdentityException;
import com.unicompanion.identity.domain.UniversityEmailPolicy;
import com.unicompanion.identity.domain.VerificationCodes;
import com.unicompanion.identity.infrastructure.persistence.EmailVerification;
import com.unicompanion.identity.infrastructure.persistence.EmailVerificationRepository;
import com.unicompanion.identity.infrastructure.persistence.UserAccount;
import com.unicompanion.identity.infrastructure.persistence.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EmailVerificationService {

    static final Duration RESEND_AFTER = Duration.ofSeconds(60);

    private final UserAccountRepository users;
    private final EmailVerificationRepository challenges;
    private final VerificationMailer mailer;
    private final UniversityEmailPolicy policy;
    private final AuditService audit;
    private final Duration codeTtl;
    private final int maxAttempts;
    private final String pepper;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(
            UserAccountRepository users,
            EmailVerificationRepository challenges,
            VerificationMailer mailer,
            AuditService audit,
            IdentityProperties properties
    ) {
        this.users = users;
        this.challenges = challenges;
        this.mailer = mailer;
        this.audit = audit;
        IdentityProperties.Verification verification = properties.identity().verification();
        this.policy = new UniversityEmailPolicy(verification.domains());
        this.codeTtl = verification.codeTtl();
        this.maxAttempts = verification.maxAttempts();
        this.pepper = properties.jwt().secret();
    }

    @Transactional
    public Requested request(UUID userId, String correlationId) {
        UserAccount account = users.findById(userId).orElseThrow(IdentityException::unauthenticated);
        if (account.isEmailVerified()) {
            return new Requested("ALREADY_VERIFIED", null);
        }
        if (!policy.accepts(account.getEmail())) {
            throw IdentityException.notUniversityEmail();
        }
        if (!mailer.canSend()) {
            throw IdentityException.verificationUnavailable();
        }
        Instant now = Instant.now();
        challenges.findFirstByUserIdOrderByCreatedAtDesc(userId).ifPresent(previous -> {
            if (previous.getCreatedAt().plus(RESEND_AFTER).isAfter(now)) {
                throw IdentityException.verificationTooSoon();
            }
        });
        challenges.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(userId).ifPresent(open -> open.consume(now));
        String code = VerificationCodes.generate(random);
        EmailVerification challenge = EmailVerification.issue(
                UUID.randomUUID(),
                userId,
                account.getEmail(),
                VerificationCodes.hash(pepper, userId, code),
                now.plus(codeTtl),
                now
        );
        challenges.save(challenge);
        audit.record(
                userId,
                "EMAIL_VERIFICATION_REQUESTED",
                "account",
                userId.toString(),
                Map.of("domain", UniversityEmailPolicy.domainOf(account.getEmail())),
                correlationId
        );
        mailer.sendCode(account.getEmail(), code);
        return new Requested("REQUESTED", mailer.delivery());
    }

    public record Requested(String status, String delivery) {
    }

    @Transactional(noRollbackFor = IdentityException.class)
    public AccountView confirm(UUID userId, String submitted, String correlationId) {
        UserAccount account = users.findById(userId).orElseThrow(IdentityException::unauthenticated);
        if (account.isEmailVerified()) {
            return AccountView.from(account);
        }
        String code = VerificationCodes.normalize(submitted);
        EmailVerification challenge = challenges.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(userId)
                .orElseThrow(IdentityException::verificationCodeInvalid);
        Instant now = Instant.now();
        if (!now.isBefore(challenge.getExpiresAt())) {
            throw IdentityException.verificationExpired();
        }
        if (challenge.getAttemptCount() >= maxAttempts) {
            throw IdentityException.verificationExhausted();
        }
        if (!VerificationCodes.matches(challenge.getCodeHash(), pepper, userId, code)) {
            challenge.recordMiss();
            if (challenge.getAttemptCount() >= maxAttempts) {
                throw IdentityException.verificationExhausted();
            }
            throw IdentityException.verificationCodeInvalid();
        }
        challenge.consume(now);
        account.markEmailVerified(now);
        audit.record(
                userId,
                "EMAIL_VERIFIED",
                "account",
                userId.toString(),
                Map.of("domain", UniversityEmailPolicy.domainOf(account.getEmail())),
                correlationId
        );
        return AccountView.from(account);
    }
}
