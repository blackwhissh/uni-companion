package com.unicompanion.learning.ingestion;

import org.springframework.web.client.HttpClientErrorException;

import java.util.function.Supplier;

final class QuotaAwareRetry {

    static final int MAX_ATTEMPTS = 5;

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private QuotaAwareRetry() {
    }

    static <T> T execute(Supplier<T> action) {
        return execute(action, Thread::sleep);
    }

    static <T> T execute(Supplier<T> action, Sleeper sleeper) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) {
                    throw wrap(ex);
                }
                try {
                    sleeper.sleep(delayMillis(attempt));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw wrap(ex);
                }
            }
        }
        throw wrap(last);
    }

    static boolean isRetryable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpClientErrorException http && http.getStatusCode().value() == 429) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static long delayMillis(int failedAttempt) {
        return Math.min(30_000L, 2_000L * (1L << Math.max(0, failedAttempt - 1)));
    }

    static RuntimeException wrap(RuntimeException ex) {
        if (ex instanceof EmbeddingException) {
            return ex;
        }
        if (isRetryable(ex)) {
            return EmbeddingException.quota(ex);
        }
        return ex;
    }
}
