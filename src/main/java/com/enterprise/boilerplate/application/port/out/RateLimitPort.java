package com.enterprise.boilerplate.application.port.out;

import java.time.Duration;

public interface RateLimitPort {

    /**
     * Increments the counter for {@code key} within the given {@code window} and
     * returns the new value. Returns {@code -1} when the backing store is
     * unavailable so callers can degrade gracefully.
     */
    long increment(String key, Duration window);
}
