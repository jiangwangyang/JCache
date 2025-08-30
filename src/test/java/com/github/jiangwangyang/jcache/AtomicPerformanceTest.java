package com.github.jiangwangyang.jcache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class AtomicPerformanceTest {

    static final int TEST_THREADS = 100;
    static final int TEST_INCREMENTS = 100000;

    @Test
    void testSynchronized() throws InterruptedException {
        final int[] count = new int[1];
        testIncrement(() -> {
            synchronized (count) {
                count[0]++;
            }
        });
    }

    @Test
    void testAtomicInteger() throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger();
        testIncrement(atomicInteger::incrementAndGet);
    }

    @Test
    void testLongAdder() throws InterruptedException {
        LongAdder longAdder = new LongAdder();
        testIncrement(longAdder::increment);
    }

    private void testIncrement(Runnable incrementRunnable) throws InterruptedException {
        ExecutorService threadPool = Executors.newFixedThreadPool(TEST_THREADS);
        List<Callable<Object>> taskList = new ArrayList<>(TEST_THREADS);
        for (int i = 0; i < TEST_THREADS; i++) {
            taskList.add(() -> {
                for (int j = 0; j < TEST_INCREMENTS; j++) {
                    incrementRunnable.run();
                }
                return null;
            });
        }
        threadPool.invokeAll(taskList);
        threadPool.shutdown();
    }

}
