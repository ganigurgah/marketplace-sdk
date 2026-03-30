package io.marketplace.sdk.ratelimit;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TokenBucketRateLimiter {
    private final Semaphore semaphore;
    private final int maxPermits;
    private final ScheduledExecutorService scheduler;

    public TokenBucketRateLimiter(int permitsPerSecond) {
        this.maxPermits = permitsPerSecond;
        this.semaphore = new Semaphore(permitsPerSecond);
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "RateLimiter-Refill");
            t.setDaemon(true);
            return t;
        });

        this.scheduler.scheduleAtFixedRate(() -> {
            int currentPermits = semaphore.availablePermits();
            int toRefill = maxPermits - currentPermits;
            if (toRefill > 0) {
                semaphore.release(toRefill);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }

    public void penalize(int seconds) {
        // Example: drain permits to simulate blocking, or use a backoff state.
        semaphore.drainPermits();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
