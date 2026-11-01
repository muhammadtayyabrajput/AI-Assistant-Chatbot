package com.fintechassist.chat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private static final int PURGE_THRESHOLD = 10_000;

    private final int max;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int max, Duration window) {
        this.max = max;
        this.windowMillis = window.toMillis();
    }

    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        if (hits.size() > PURGE_THRESHOLD) purge(now);
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }
    }

    private void prune(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > windowMillis) q.pollFirst();
    }

    private void purge(long now) {
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                prune(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }
}
