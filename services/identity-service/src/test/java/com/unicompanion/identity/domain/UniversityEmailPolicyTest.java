package com.unicompanion.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UniversityEmailPolicyTest {

    private final UniversityEmailPolicy policy = new UniversityEmailPolicy(List.of("uni-siegen.de", "@UNI-Siegen.DE"));

    @Test
    void acceptsOnlyTheListedDomain() {
        assertThat(policy.accepts("Ada@Uni-Siegen.de")).isTrue();
        assertThat(policy.accepts("ada@gmail.com")).isFalse();
        assertThat(policy.accepts("ada@cs.uni-siegen.de")).isFalse();
        assertThat(policy.accepts("ada@uni-siegen.de.evil.test")).isFalse();
        assertThat(policy.accepts("not-an-email")).isFalse();
        assertThat(policy.accepts("ada@@uni-siegen.de")).isFalse();
    }
}
