package com.github.jiangwangyang.jcache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SegmentedLRUCacheTest {

    @Test
    void testGetSetRemoveExpire() throws InterruptedException {
        JCache<Object, Object> cache = new SegmentedLRUCache<>(100, 10, 0.2);
        assertEquals(1, cache.get(1, 100, 100, (key -> key)));
        assertEquals(1, cache.getIfPresent(1));
        cache.remove(1);
        assertNull(cache.getIfPresent(1));
        cache.put(1, 1, 100, 100);
        assertEquals(1, cache.getIfPresent(1));
        cache.remove(1);
        assertNull(cache.getIfPresent(1));
        assertEquals(1, cache.get(1, 100, 100, (key -> key)));
        Thread.sleep(150);
        assertNull(cache.getIfPresent(1));
    }

    @Test
    void testCapacity() {
        JCache<Object, Object> cache = new SegmentedLRUCache<>(10, 1, 0.2);
        cache.get(2, 100, 100, key -> key);
        cache.get(3, 100, 100, key -> key);
        for (int i = 0; i < 100; i++) {
            cache.get(i, 60000, 60000, (key -> key));
        }
        assertEquals(10, cache.stats().size());
        for (int i = 0; i < 100; i++) {
            if (i == 2 || i == 3) {
                assertNotNull(cache.getIfPresent(i));
            } else if (i >= 92) {
                assertNotNull(cache.getIfPresent(i));
            } else {
                assertNull(cache.getIfPresent(i));
            }
        }
    }

    @Test
    void testBreakdownAndPenetration() throws InterruptedException {
        JCache<Object, Object> cache = new SegmentedLRUCache<>(100, 10, 0.2);
        AtomicInteger atomicInteger = new AtomicInteger(0);
        ExecutorService threadPool = Executors.newFixedThreadPool(100);
        List<Callable<Void>> taskList = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            taskList.add(() -> {
                cache.get(1, 60000, 60000, (key -> {
                    atomicInteger.getAndIncrement();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                }));
                return null;
            });
        }
        threadPool.invokeAll(taskList);
        threadPool.shutdown();
        assertEquals(1, atomicInteger.get());
    }

    @Test
    void testMultiThreads() throws InterruptedException {
        JCache<Integer, Integer> cache = new SegmentedLRUCache<>(100, 10, 0.2);
        ExecutorService threadPool = Executors.newFixedThreadPool(100);
        List<Callable<Void>> taskList = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            taskList.add(() -> {
                for (int j = 0; j < 1000; j++) {
                    Integer value = cache.get(ThreadLocalRandom.current().nextInt(100), 100, 200, (key -> key));
                    assertNotNull(value);
                }
                return null;
            });
        }
        threadPool.invokeAll(taskList);
        threadPool.shutdown();
        for (int i = 0; i < 100; i++) {
            assertEquals(i, cache.getIfPresent(i));
        }
        Thread.sleep(200);
        for (int i = 0; i < 100; i++) {
            assertNull(cache.getIfPresent(i));
        }
    }

}
