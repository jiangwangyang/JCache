package com.github.jiangwangyang.jcache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class SegmentedLRUCachePerformanceTest {

    @Test
    public void test() throws InterruptedException {
        testSegmentedLRUCachePerformance(75, 0, 1000, 2000);
    }

    private void testSegmentedLRUCachePerformance(int hotPercent, int ioMillis, long minExpireMillis, long maxExpireMillis) throws InterruptedException {
        JCache<Integer, Long> cache = new SegmentedLRUCache<>(10000, 100, 0.25);
        ExecutorService threadPool = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 100; i++) {
            threadPool.execute(() -> {
                for (;;) {
                    Integer key = ThreadLocalRandom.current().nextInt(100) < hotPercent ?
                            1 : ThreadLocalRandom.current().nextInt();
                    cache.get(key, minExpireMillis, maxExpireMillis, k -> {
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
            System.out.println("QPS:" + (cur.minus(prev)).requestPerSecond());
            prev = cur;
        }
    }

}
