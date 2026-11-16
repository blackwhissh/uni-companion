package com.unicompanion.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodesTest {

    @Test
    void storesAHashThatMatchesOnlyTheSameUserAndCode() {
        UUID userId = UUID.randomUUID();
        String code = "042013";

        String hash = VerificationCodes.hash("pepper", userId, code);

        assertThat(hash).doesNotContain(code);
        assertThat(VerificationCodes.matches(hash, "pepper", userId, VerificationCodes.normalize("042 013"))).isTrue();
        assertThat(VerificationCodes.matches(hash, "pepper", userId, "042014")).isFalse();
        assertThat(VerificationCodes.matches(hash, "other", userId, code)).isFalse();
        assertThat(VerificationCodes.matches(hash, "pepper", UUID.randomUUID(), code)).isFalse();
        assertThat(VerificationCodes.normalize("12ab")).isEmpty();
    }
}
