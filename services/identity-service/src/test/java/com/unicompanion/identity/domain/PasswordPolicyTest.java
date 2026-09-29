package com.unicompanion.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void rejectsPasswordsShorterThanEightCharacters() {
        assertThatThrownBy(() -> PasswordPolicy.check("short"))
                .isInstanceOf(IdentityException.class)
                .extracting(ex -> ((IdentityException) ex).code())
                .isEqualTo(IdentityException.Code.WEAK_PASSWORD);
    }

    @Test
    void acceptsAnEightCharacterPassword() {
        assertThatCode(() -> PasswordPolicy.check("12345678")).doesNotThrowAnyException();
    }
}
