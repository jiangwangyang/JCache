package com.github.jiangwangyang.jcache;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class SegmentedLRUCacheTest {

    @Test
    public void testGetSetRemoveExpire() throws Exception {
        JCache<Object, Object> cache = new SegmentedLRUCache<>(10000, 100, 0.25);
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
    public void testCapacity() {
        JCache<Object, Object> cache = new SegmentedLRUCache<>(10, 1, 0.2);
        cache.get(2, 100, 100, key -> key);
        cache.get(3, 100, 100, key -> key);
        for (int i = 0; i < 1000; i++) {
            cache.get(i, 60000, 60000, (key -> key));
        }
        assertEquals(10, cache.stats().size());
        for (int i = 0; i < 1000; i++) {
            if (i == 2 || i == 3) {
                assertNotNull(cache.getIfPresent(i));
            } else if (i >= 992){
                assertNotNull(cache.getIfPresent(i));
            } else {
                assertNull(cache.getIfPresent(i));
            }
        }
    }

    @Test
    public void testBreakdownAndPenetration() throws Exception {
        JCache<Object, Object> cache = new SegmentedLRUCache<>(10000, 100, 0.25);
        AtomicInteger atomicInteger = new AtomicInteger(0);
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                try {
                    cache.get(1, 60000, 60000, (key -> {
                        atomicInteger.getAndIncrement();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return null;
                    }));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        for (int i = 0; i < 10; i++) {
            threads[i].start();
        }
        for (int i = 0; i < 10; i++) {
            threads[i].join();
        }
        assertEquals(1, atomicInteger.get());
    }

}
