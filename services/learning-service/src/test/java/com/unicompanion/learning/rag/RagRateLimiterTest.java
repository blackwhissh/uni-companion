package com.unicompanion.learning.rag;

import com.unicompanion.learning.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagRateLimiterTest {

    @Test
    void allowsWithinPerMinuteBudgetThenRejects() {
        RagRateLimiter limiter = new RagRateLimiter(new RagProperties(6, 2, 100, false, 0));
        UUID user = UUID.randomUUID();

        assertThatCode(() -> limiter.check(user)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(user)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many questions");
    }

    @Test
    void enforcesDailyBudgetIndependently() {
        RagRateLimiter limiter = new RagRateLimiter(new RagProperties(6, 100, 2, false, 0));
        UUID user = UUID.randomUUID();

        assertThatCode(() -> limiter.check(user)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(user)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Daily Q&A limit");
    }
}
