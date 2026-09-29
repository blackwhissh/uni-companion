package com.unicompanion.identity.infrastructure.persistence;

import com.unicompanion.identity.domain.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 500)
    private String interests;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    protected UserAccount() {
    }

    public static UserAccount create(UUID id, String email, String passwordHash, String displayName, Set<Role> roles, Instant now) {
        UserAccount account = new UserAccount();
        account.id = id;
        account.email = email;
        account.passwordHash = passwordHash;
        account.displayName = displayName;
        account.roles = new HashSet<>(roles);
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    public void updateProfile(String displayName, String interests, Instant now) {
        this.displayName = displayName;
        this.interests = interests;
        this.updatedAt = now;
    }

    public void ensureRoles(Set<Role> required, Instant now) {
        if (this.roles.containsAll(required)) {
            return;
        }
        this.roles.addAll(required);
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getInterests() {
        return interests;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
