package com.github.jiangwangyang.jcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

public class CachePerformanceTest {

    static final int TEST_SECONDS = 10;
    static final int TEST_THREADS = 100;
    static final int HOT_PERCENT = 75;
    static final int IO_MILLIS = 0;
    static final long MIN_EXPIRE_MILLIS = 1000;
    static final long MAX_EXPIRE_MILLIS = 2000;

    @Test
    void testConcurrentHashMapPerformance() throws InterruptedException {
        Map<Integer, Long> cache = new ConcurrentHashMap<>(10000);
        CacheGetFunction<Integer, Long> cacheGetFunction = (key, loadValueFunction, minExpireMillis, maxExpireMillis)
                -> cache.computeIfAbsent(key, loadValueFunction);
        testPerformance(cacheGetFunction);
    }

    @Test
    void testSegmentedLRUCachePerformance() throws InterruptedException {
        JCache<Integer, Long> cache = new SegmentedLRUCache<>(10000, 100, 0.25);
        CacheGetFunction<Integer, Long> cacheGetFunction = (key, loadValueFunction, minExpireMillis, maxExpireMillis)
                -> cache.get(key, minExpireMillis, maxExpireMillis, loadValueFunction);
        testPerformance(cacheGetFunction);
    }

    @Test
    void testCaffeinePerformance() throws InterruptedException {
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
        CacheGetFunction<Integer, Long> cacheGetFunction = (key, loadValueFunction, minExpireMillis, maxExpireMillis)
                -> cache.get(key, loadValueFunction);
        testPerformance(cacheGetFunction);
    }

    private void testPerformance(CacheGetFunction<Integer, Long> cacheGetFunction) throws InterruptedException {
        Function<Integer, Long> loadValueFunction = k -> {
            if (IO_MILLIS > 0) {
                try {
                    Thread.sleep(IO_MILLIS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return ThreadLocalRandom.current().nextLong(MIN_EXPIRE_MILLIS, MAX_EXPIRE_MILLIS);
        };
        LongAdder longAdder = new LongAdder();
        ExecutorService threadPool = Executors.newFixedThreadPool(TEST_THREADS);
        for (int i = 0; i < TEST_THREADS; i++) {
            threadPool.execute(() -> {
                for (; ; ) {
                    Integer key = ThreadLocalRandom.current().nextInt(100) < HOT_PERCENT ?
                            1 : ThreadLocalRandom.current().nextInt();
                    cacheGetFunction.apply(key, loadValueFunction, MIN_EXPIRE_MILLIS, MAX_EXPIRE_MILLIS);
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
    }

    private interface CacheGetFunction<K, V> {
        void apply(K key, Function<K, V> loadValueFunction, long minExpireMillis, long maxExpireMillis);
    }

}
