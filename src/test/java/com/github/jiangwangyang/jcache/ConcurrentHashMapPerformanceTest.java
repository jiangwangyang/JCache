package com.github.jiangwangyang.jcache;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentHashMapPerformanceTest {

    @Test
    public void test() throws InterruptedException {
        testConcurrentHashMapPerformance(75, 0);
    }

    private void testConcurrentHashMapPerformance(int hotPercent, int ioMillis) throws InterruptedException {
        LongAdder longAdder = new LongAdder();
        Map<Integer, Long> cache = new ConcurrentHashMap<>(10000);
        ExecutorService threadPool = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 100; i++) {
            threadPool.execute(() -> {
                for (;;) {
                    Integer key = ThreadLocalRandom.current().nextInt(100) < hotPercent ?
                            1 : ThreadLocalRandom.current().nextInt();
                    cache.computeIfAbsent(key, k -> {
                        if (ioMillis > 0) {
                            try {
                                Thread.sleep(ioMillis);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return ThreadLocalRandom.current().nextLong(1000, 2000);
                    });
                    longAdder.increment();
                }
            });
        }
        long prev = longAdder.sum();
        long cur;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1000);
            cur = longAdder.sum();
            System.out.println("QPS:" + (double) (cur - prev));
            prev = cur;
        }
    }

}
