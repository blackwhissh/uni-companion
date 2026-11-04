package com.unicompanion.learning.matching.application;

import com.unicompanion.learning.matching.domain.ContactStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContactExchangeTest {

    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALAN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GRACE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void pendingAndDeclinedHideBothAddresses() {
        assertThat(ContactExchange.emailForViewer(ContactStatus.PENDING, ADA, ADA, ALAN, "ada@uni.test", "alan@uni.test"))
                .isNull();
        assertThat(ContactExchange.emailForViewer(ContactStatus.DECLINED, ALAN, ADA, ALAN, "ada@uni.test", "alan@uni.test"))
                .isNull();
    }

    @Test
    void acceptedShowsOnlyTheOtherPersonsAddress() {
        assertThat(ContactExchange.emailForViewer(ContactStatus.ACCEPTED, ADA, ADA, ALAN, "ada@uni.test", "alan@uni.test"))
                .isEqualTo("alan@uni.test");
        assertThat(ContactExchange.emailForViewer(ContactStatus.ACCEPTED, ALAN, ADA, ALAN, "ada@uni.test", "alan@uni.test"))
                .isEqualTo("ada@uni.test");
    }

    @Test
    void aThirdStudentSeesNothing() {
        assertThat(ContactExchange.emailForViewer(ContactStatus.ACCEPTED, GRACE, ADA, ALAN, "ada@uni.test", "alan@uni.test"))
                .isNull();
    }
}
