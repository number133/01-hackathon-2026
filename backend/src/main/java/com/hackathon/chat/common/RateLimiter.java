package com.hackathon.chat.common;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-user token-bucket limiter. Each caller gets a named bucket; the first
 * request per key creates it with the configured capacity and refill rate.
 * Thread-safe via {@link ConcurrentHashMap} + synchronized bucket state.
 */
@Component
public class RateLimiter {

    private final Map<String, Map<UUID, Bucket>> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String scope, UUID userId, int capacity, double refillPerSecond) {
        Map<UUID, Bucket> scoped = buckets.computeIfAbsent(scope, k -> new ConcurrentHashMap<>());
        Bucket b = scoped.computeIfAbsent(userId,
                k -> new Bucket(capacity, refillPerSecond, clock.millis()));
        return b.tryAcquire(capacity, refillPerSecond, clock.millis());
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillMs;

        Bucket(int capacity, double refillPerSecond, long nowMs) {
            this.tokens = capacity;
            this.lastRefillMs = nowMs;
        }

        synchronized boolean tryAcquire(int capacity, double refillPerSecond, long nowMs) {
            long dt = nowMs - lastRefillMs;
            if (dt > 0) {
                tokens = Math.min(capacity, tokens + dt * refillPerSecond / 1000.0);
                lastRefillMs = nowMs;
            }
            if (tokens < 1.0) return false;
            tokens -= 1.0;
            return true;
        }
    }
}
