package com.unicompanion.identity.application;

import com.unicompanion.identity.domain.IdentityException;
import com.unicompanion.identity.domain.PasswordPolicy;
import com.unicompanion.identity.domain.Role;
import com.unicompanion.identity.infrastructure.persistence.UserAccount;
import com.unicompanion.identity.infrastructure.persistence.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AccountService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password");
    }

    @Transactional
    public AccountView register(String email, String rawPassword, String displayName) {
        AccountView account = create(email, rawPassword, displayName, Set.of(Role.STUDENT));
        log.info("Registered student userId={} email={}", account.id(), account.email());
        return account;
    }

    @Transactional
    public void seedCourseAdmin(String email, String rawPassword, String displayName) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            log.info("Course admin already present: {}", normalized);
            return;
        }
        create(normalized, rawPassword, displayName, Set.of(Role.COURSE_ADMIN));
        log.info("Seeded course admin {}", normalized);
    }

    @Transactional
    public void seedAccount(String email, String rawPassword, String displayName, Set<Role> roles) {
        String normalized = normalizeEmail(email);
        var existing = users.findByEmail(normalized);
        if (existing.isPresent()) {
            existing.get().ensureRoles(roles, Instant.now());
            return;
        }
        Instant now = Instant.now();
        users.save(UserAccount.create(
                UUID.randomUUID(),
                normalized,
                passwordEncoder.encode(rawPassword),
                displayName.trim(),
                roles,
                now));
        log.info("Seeded account {}", normalized);
    }

    @Transactional(readOnly = true)
    public AccountView login(String email, String rawPassword) {
        String normalized = normalizeEmail(email);
        var account = users.findByEmail(normalized);
        if (account.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            log.warn("Login failed: unknown email={}", normalized);
            throw IdentityException.invalidCredentials();
        }
        if (!passwordEncoder.matches(rawPassword, account.get().getPasswordHash())) {
            log.warn("Login failed: bad password userId={} email={}", account.get().getId(), normalized);
            throw IdentityException.invalidCredentials();
        }
        log.info("Login ok userId={} email={} roles={}", account.get().getId(), normalized, account.get().getRoles());
        return AccountView.from(account.get());
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID id) {
        return users.findById(id).map(AccountView::from).orElseThrow(IdentityException::unauthenticated);
    }

    @Transactional
    public AccountView updateProfile(UUID id, String displayName, String interests) {
        UserAccount account = users.findById(id).orElseThrow(IdentityException::unauthenticated);
        account.updateProfile(displayName.trim(), blankToNull(interests), Instant.now());
        log.info("Updated profile userId={}", id);
        return AccountView.from(account);
    }

    private AccountView create(String email, String rawPassword, String displayName, Set<Role> roles) {
        PasswordPolicy.check(rawPassword);
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw IdentityException.emailTaken();
        }
        String name = displayName.trim();
        if (name.isEmpty() || roles.isEmpty()) {
            throw new IllegalArgumentException("Display name and a role are required.");
        }
        Instant now = Instant.now();
        UserAccount account = UserAccount.create(
                UUID.randomUUID(),
                normalized,
                passwordEncoder.encode(rawPassword),
                name,
                roles,
                now);
        return AccountView.from(users.save(account));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
