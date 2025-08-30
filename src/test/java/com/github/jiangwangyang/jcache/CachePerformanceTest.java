package com.github.jiangwangyang.jcache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

public class CachePerformanceTest {

    static final int TEST_SECONDS = 10;
    static final int TEST_THREADS = 100;
    static final int HOT_PERCENT = 75;
    static final int IO_MILLIS = 0;
    static final long MIN_EXPIRE_MILLIS = 1000;
    static final long MAX_EXPIRE_MILLIS = 2000;

    @Test
    void testSegmentedLRUCachePerformance() throws InterruptedException {
        testPerformance(new SegmentedLRUCache<>(10000, 100, 0.25));
    }

    @Test
    void testCaffeinePerformance() throws InterruptedException {
        testPerformance(new CaffeineWrapper<>(10000));
    }

    private void testPerformance(JCache<Integer, Integer> cache) throws InterruptedException {
        LongAdder longAdder = new LongAdder();
        ExecutorService threadPool = Executors.newFixedThreadPool(TEST_THREADS);
        for (int i = 0; i < TEST_THREADS; i++) {
            threadPool.execute(() -> {
                for (; ; ) {
                    Integer key = ThreadLocalRandom.current().nextInt(100) < HOT_PERCENT ?
                            1 : ThreadLocalRandom.current().nextInt();
                    cache.get(key, MIN_EXPIRE_MILLIS, MAX_EXPIRE_MILLIS, k -> {
                        if (IO_MILLIS > 0) {
                            try {
                                Thread.sleep(IO_MILLIS);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return k;
                    });
                    longAdder.increment();
                }
            });
        }
        long prev = 0;
        long cur;
        for (int i = 0; i < TEST_SECONDS; i++) {
            Thread.sleep(1000);
            cur = longAdder.sum();
            System.out.println("QPS:" + (double) (cur - prev));
            prev = cur;
        }
        threadPool.shutdownNow();
    }

}
