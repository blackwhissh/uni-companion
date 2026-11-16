package com.unicompanion.learning.rag;

import com.unicompanion.learning.config.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-user limits for expensive RAG calls (embed + LLM).
 * Sufficient for single-instance local/QA; replace with Redis for multi-node prod.
 */
@Component
public class RagRateLimiter {

    private static final long MINUTE_MS = 60_000L;
    private static final long DAY_MS = 86_400_000L;

    private final int requestsPerMinute;
    private final int requestsPerDay;
    private final Map<UUID, Deque<Long>> minuteWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> dayWindows = new ConcurrentHashMap<>();

    public RagRateLimiter(RagProperties properties) {
        this.requestsPerMinute = Math.max(1, properties.requestsPerMinute());
        this.requestsPerDay = Math.max(1, properties.requestsPerDay());
    }

    public void check(UUID userId) {
        long now = System.currentTimeMillis();
        if (!allow(minuteWindows, userId, now, MINUTE_MS, requestsPerMinute)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many questions in a short time. Wait a minute and try again."
            );
        }
        if (!allow(dayWindows, userId, now, DAY_MS, requestsPerDay)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Daily Q&A limit reached. Try again tomorrow."
            );
        }
    }

    private static boolean allow(
            Map<UUID, Deque<Long>> windows,
            UUID userId,
            long now,
            long windowMs,
            int limit
    ) {
        Deque<Long> stamps = windows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (stamps) {
            long cutoff = now - windowMs;
            while (!stamps.isEmpty() && stamps.peekFirst() < cutoff) {
                stamps.removeFirst();
            }
            if (stamps.size() >= limit) {
                return false;
            }
            stamps.addLast(now);
            return true;
        }
    }
}
