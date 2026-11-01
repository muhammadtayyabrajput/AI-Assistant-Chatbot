package com.fintechassist.auth;

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

    public boolean isBlocked(String key) {
        Deque<Long> q = hits.get(key);
        if (q == null) return false;
        synchronized (q) {
            prune(q, System.currentTimeMillis());
            return q.size() >= max;
        }
    }

    public void record(String key) {
        long now = System.currentTimeMillis();
        if (hits.size() > PURGE_THRESHOLD) purge(now);
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q, now);
            q.addLast(now);
        }
    }

    public boolean tryAcquire(String key) {
        if (isBlocked(key)) return false;
        record(key);
        return true;
    }

    public void reset(String key) {
        hits.remove(key);
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
