package com.unicompanion.learning.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotaAwareRetryTest {

    @Test
    void retriesTooManyRequestsThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger slept = new AtomicInteger();
        String result = QuotaAwareRetry.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw tooManyRequests();
            }
            return "ok";
        }, millis -> slept.addAndGet((int) millis));

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(slept.get()).isEqualTo(2000 + 4000);
    }

    @Test
    void mapsExhaustedQuotaToEmbeddingException() {
        assertThatThrownBy(() -> QuotaAwareRetry.execute(() -> {
            throw tooManyRequests();
        }, millis -> {}))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("quota");
    }

    @Test
    void doesNotRetryUnrelatedFailures() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> QuotaAwareRetry.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void delayGrowsThenCaps() {
        assertThat(QuotaAwareRetry.delayMillis(1)).isEqualTo(2000);
        assertThat(QuotaAwareRetry.delayMillis(2)).isEqualTo(4000);
        assertThat(QuotaAwareRetry.delayMillis(5)).isEqualTo(30000);
    }

    private static HttpClientErrorException tooManyRequests() {
        return HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                null,
                "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }
}
