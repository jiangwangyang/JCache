package com.github.jiangwangyang.jcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.checkerframework.checker.index.qual.NonNegative;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class CaffeinePerformanceTest {

    @Test
    public void test() throws InterruptedException {
        testCaffeinePerformance(75, 0, 1000, 2000);
    }

    private void testCaffeinePerformance(int hotPercent, int ioMillis, long minExpireMillis, long maxExpireMillis) throws InterruptedException {
        Cache<Integer, Long> cache = Caffeine.newBuilder()
                .initialCapacity(10000)
                .maximumSize(10000)
                .expireAfter(new Expiry<Integer, Long>() {
                    @Override
                    public long expireAfterCreate(Integer k, Long v, long l) {
                        return Duration.ofMillis(v).toNanos();
                    }
                    @Override
                    public long expireAfterUpdate(Integer k, Long v, long l1, @NonNegative long l2) {
                        return l2;
                    }
                    @Override
                    public long expireAfterRead(Integer k, Long v, long l1, @NonNegative long l2) {
                        return l2;
                    }
                })
                .recordStats()
                .build();
        ExecutorService threadPool = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 100; i++) {
            threadPool.execute(() -> {
                for (;;) {
                    Integer key = ThreadLocalRandom.current().nextInt(100) < hotPercent ?
                            1 : ThreadLocalRandom.current().nextInt();
                    cache.get(key, k -> {
                        if (ioMillis > 0) {
                            try {
                                Thread.sleep(ioMillis);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return ThreadLocalRandom.current().nextLong(minExpireMillis, maxExpireMillis);
                    });
                }
            });
        }
        CacheStats prev = cache.stats();
        CacheStats cur;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1000);
            cur = cache.stats();
            System.out.println("QPS:" + (double) (cur.minus(prev)).requestCount());
            prev = cur;
        }
    }

}
