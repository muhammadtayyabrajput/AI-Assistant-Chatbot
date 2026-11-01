package com.fintechassist.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void blocksOnlyOnceTheLimitIsReached() {
        RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(1));
        for (int i = 0; i < 3; i++) {
            assertFalse(limiter.isBlocked("k"));
            limiter.record("k");
        }
        assertTrue(limiter.isBlocked("k"));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));
        limiter.record("a");
        assertTrue(limiter.isBlocked("a"));
        assertFalse(limiter.isBlocked("b"));
    }

    @Test
    void resetClearsTheCounter() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMinutes(1));
        limiter.record("a");
        limiter.reset("a");
        assertFalse(limiter.isBlocked("a"));
    }

    @Test
    void oldHitsExpire() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMillis(50));
        limiter.record("a");
        assertTrue(limiter.isBlocked("a"));
        Thread.sleep(80);
        assertFalse(limiter.isBlocked("a"));
    }

    @Test
    void tryAcquireCountsEachCall() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
    }
}
