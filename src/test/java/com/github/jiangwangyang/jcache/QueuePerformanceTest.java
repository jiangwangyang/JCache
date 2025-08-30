package com.github.jiangwangyang.jcache;

import org.jctools.queues.MpmcArrayQueue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class QueuePerformanceTest {

    static final int TEST_THREADS = 10;
    static final int TEST_ELEMENTS = 1000000;

    @Test
    void testLinkedBlockingQueue() throws InterruptedException {
        testQueue(new LinkedBlockingQueue<>());
    }

    @Test
    void testConcurrentLinkedQueue() throws InterruptedException {
        testQueue(new ConcurrentLinkedQueue<>());
    }

    @Test
    void testConcurrentLinkedDeque() throws InterruptedException {
        testQueue(new ConcurrentLinkedDeque<>());
    }

    @Test
    void testMpmcArrayQueue() throws InterruptedException {
        testQueue(new MpmcArrayQueue<>(TEST_ELEMENTS));
    }

    void testQueue(Queue<Integer> queue) throws InterruptedException {
        ExecutorService threadPool = Executors.newFixedThreadPool(TEST_THREADS);
        List<Callable<Object>> taskList = new ArrayList<>(TEST_THREADS);
        for (int i = 0; i < TEST_THREADS; i++) {
            taskList.add(() -> {
                for (int j = 0; j < 1000000; j++) {
                    queue.offer(j);
                    Integer poll = queue.poll();
                    assert poll != null;
                }
                return null;
            });
        }
        threadPool.invokeAll(taskList);
        threadPool.shutdown();
        assert queue.isEmpty();
    }

}
