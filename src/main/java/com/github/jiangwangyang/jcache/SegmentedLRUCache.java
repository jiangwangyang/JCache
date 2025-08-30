package com.github.jiangwangyang.jcache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

public class SegmentedLRUCache<K, V> implements JCache<K, V> {

    static final int HASH_BITS = 0x7fffffff;
    private final Map<K, Node<K, V>> map;
    private final SegmentedLruQueue<K, V>[] segmentedLruQueues;
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder hitCount = new LongAdder();
    private final long startTime;

    public SegmentedLRUCache(int capacity, int segmentNum, double hotRatio) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be greater than 0");
        }
        if (segmentNum < 0) {
            throw new IllegalArgumentException("segmentNum must be greater than 0");
        }
        if (hotRatio < 0 || hotRatio > 1) {
            throw new IllegalArgumentException("hotRatio must be between 0 and 1");
        }
        int segmentCapacity = capacity / segmentNum;
        int segmentHotCapacity = (int) (segmentCapacity * hotRatio);
        int segmentColdCapacity = segmentCapacity - segmentHotCapacity;
        this.map = new ConcurrentHashMap<>(segmentNum * segmentCapacity << 1);
        this.segmentedLruQueues = new SegmentedLruQueue[segmentNum];
        for (int i = 0; i < segmentNum; i++) {
            segmentedLruQueues[i] = new SegmentedLruQueue<>(segmentHotCapacity, segmentColdCapacity);
        }
        startTime = System.currentTimeMillis();
    }

    private SegmentedLruQueue<K, V> getSegmentedLruQueue(K key) {
        int h = key.hashCode();
        return segmentedLruQueues[((h ^ (h >>> 16)) & HASH_BITS) % segmentedLruQueues.length];
    }

    private Node<K, V> getNodeIfPresent(K key) {
        requestCount.increment();
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        if (node.expireTime <= System.currentTimeMillis()) {
            map.remove(key, node);
            node.key = null;
            node.value = null;
            return null;
        }
        getSegmentedLruQueue(key).updateNode(node);
        hitCount.increment();
        return node;
    }

    @Override
    public V get(K key, long minExpireMillis, long maxExpireMillis, Function<K, V> loadValueFunction) {
        assert key != null;
        assert loadValueFunction != null;
        assert minExpireMillis >= 0;
        assert maxExpireMillis >= 0;
        assert minExpireMillis <= maxExpireMillis;
        Node<K, V> node = getNodeIfPresent(key);
        if (node != null) {
            return node.value;
        }
        SegmentedLruQueue<K, V> segmentedLruQueue = getSegmentedLruQueue(key);
        Node<K, V>[] removedNodeWrapper = new Node[1];
        boolean[] executedWrapper = new boolean[1];
        node = map.computeIfAbsent(key, (_k) -> {
            executedWrapper[0] = true;
            Node<K, V> newNode = new Node<>(key, loadValueFunction.apply(key), System.currentTimeMillis()
                    + ThreadLocalRandom.current().nextLong(minExpireMillis, maxExpireMillis + 1));
            removedNodeWrapper[0] = segmentedLruQueue.addNodeAndRemoveTail(newNode);
            return newNode;
        });
        if (executedWrapper[0] && removedNodeWrapper[0].key != null) {
            map.remove(removedNodeWrapper[0].key, removedNodeWrapper[0]);
            removedNodeWrapper[0].key = null;
            removedNodeWrapper[0].value = null;
        }
        if (!executedWrapper[0]) {
            segmentedLruQueue.updateNode(node);
        }
        return node.value;
    }

    @Override
    public V getIfPresent(K key) {
        assert key != null;
        Node<K, V> node = getNodeIfPresent(key);
        return node == null ? null : node.value;
    }

    @Override
    public void put(K key, V value, long minExpireMillis, long maxExpireMillis) {
        assert key != null;
        assert minExpireMillis >= 0;
        assert maxExpireMillis >= 0;
        assert minExpireMillis <= maxExpireMillis;
        SegmentedLruQueue<K, V> segmentedLruQueue = getSegmentedLruQueue(key);
        Node<K, V> newNode = new Node<>(key, value, System.currentTimeMillis()
                + ThreadLocalRandom.current().nextLong(minExpireMillis, maxExpireMillis + 1));
        Node<K, V> removedNode = segmentedLruQueue.addNodeAndRemoveTail(newNode);
        if (removedNode.key != null) {
            map.remove(removedNode.key, removedNode);
            removedNode.key = null;
            removedNode.value = null;
        }
        map.put(key, newNode);
    }

    @Override
    public void remove(K key) {
        assert key != null;
        Node<K, V> node = map.get(key);
        if (node != null) {
            map.remove(key, node);
            node.key = null;
            node.value = null;
        }
    }

    @Override
    public CacheStats stats() {
        return new CacheStats(map.size(), startTime, System.currentTimeMillis(), requestCount.sum(), hitCount.sum());
    }

    enum NodeStatus {
        INITIAL,
        HOT,
        COLD,
        REMOVED
    }

    static class SegmentedLruQueue<K, V> {
        Node<K, V> hotHead;
        Node<K, V> hotTail;
        Node<K, V> coldHead;
        Node<K, V> coldTail;

        SegmentedLruQueue(int hotQueueCapacity, int coldQueueCapacity) {
            assert hotQueueCapacity > 0;
            assert coldQueueCapacity > 0;
            hotHead = hotTail = new Node<>(null, null, 0);
            coldHead = coldTail = new Node<>(null, null, 0);
            for (int i = 1; i < hotQueueCapacity; i++) {
                addHotHead(new Node<>(null, null, 0));
            }
            for (int i = 1; i < coldQueueCapacity; i++) {
                addColdHead(new Node<>(null, null, 0));
            }
        }

        Node<K, V> addNodeAndRemoveTail(Node<K, V> node) {
            assert node != null;
            synchronized (this) {
                assert node.key != null;
                assert node.status == NodeStatus.INITIAL;
                node.status = NodeStatus.COLD;
                addColdHead(node);
                Node<K, V> removedNode = removeColdTail();
                removedNode.status = NodeStatus.REMOVED;
                return removedNode;
            }
        }

        void updateNode(Node<K, V> node) {
            assert node != null;
            if (node.status == NodeStatus.HOT || node.status == NodeStatus.REMOVED) {
                return;
            }
            synchronized (this) {
                if (node.status == NodeStatus.HOT || node.status == NodeStatus.REMOVED) {
                    return;
                }
                assert node.key != null;
                assert node.status != NodeStatus.INITIAL;
                removeColdNode(node);
                node.status = NodeStatus.HOT;
                addHotHead(node);
                node = removeHotTail();
                node.status = NodeStatus.COLD;
                addColdHead(node);
            }
        }

        private void addHotHead(Node<K, V> node) {
            assert node != null;
            node.next = hotHead;
            hotHead.prev = node;
            hotHead = node;
        }

        private void addColdHead(Node<K, V> node) {
            assert node != null;
            node.next = coldHead;
            coldHead.prev = node;
            coldHead = node;
        }

        private Node<K, V> removeHotTail() {
            Node<K, V> ct = hotTail;
            ct.prev.next = null;
            hotTail = ct.prev;
            ct.prev = null;
            return ct;
        }

        private Node<K, V> removeColdTail() {
            Node<K, V> ct = coldTail;
            ct.prev.next = null;
            coldTail = ct.prev;
            ct.prev = null;
            return ct;
        }

        private void removeColdNode(Node<K, V> node) {
            assert node != null;
            assert node != hotHead;
            assert node != hotTail;
            if (node == coldHead) {
                coldHead = node.next;
            } else {
                node.prev.next = node.next;
            }
            if (node == coldTail) {
                coldTail = node.prev;
            } else {
                node.next.prev = node.prev;
            }
            node.prev = null;
            node.next = null;
        }
    }

    static class Node<K, V> {
        final long expireTime;
        K key;
        V value;
        NodeStatus status = NodeStatus.INITIAL;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value, long expireTime) {
            this.key = key;
            this.value = value;
            this.expireTime = expireTime;
        }
    }

}
