package com.github.jiangwangyang.jcache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

public class SegmentedLRUCache<K, V> implements JCache<K, V> {

    static final int HASH_BITS = 0x7fffffff;
    private final Map<K, Node<K, V>> map;
    private final SegmentedLRU<K, V>[] segmentedLRUS;
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
        this.segmentedLRUS = new SegmentedLRU[segmentNum];
        for (int i = 0; i < segmentNum; i++) {
            segmentedLRUS[i] = new SegmentedLRU<>(segmentHotCapacity, segmentColdCapacity);
        }
        startTime = System.currentTimeMillis();
    }

    private SegmentedLRU<K, V> getSegmentedLRU(K key) {
        int h = key.hashCode();
        return segmentedLRUS[((h ^ (h >>> 16)) & HASH_BITS) % segmentedLRUS.length];
    }

    private Node<K, V> getNodeIfPresent(K key) {
        requestCount.increment();
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        if (node.expireTime <= System.currentTimeMillis()) {
            map.remove(key, node);
            return null;
        }
        getSegmentedLRU(key).updateNode(node);
        hitCount.increment();
        return node;
    }

    @Override
    public V get(K key, long minExpireMillis, long maxExpireMillis, Function<K, V> loadValueFunction) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (minExpireMillis < 0 || maxExpireMillis < 0) {
            throw new IllegalArgumentException("minExpireMillis and maxExpireMillis must be greater than 0");
        }
        if (maxExpireMillis < minExpireMillis) {
            throw new IllegalArgumentException("maxExpireMillis must be greater than minExpireMillis");
        }
        if (loadValueFunction == null) {
            throw new IllegalArgumentException("loadValueFunction must not be null");
        }
        Node<K, V> node = getNodeIfPresent(key);
        if (node != null) {
            return node.value;
        }
        requestCount.increment();
        SegmentedLRU<K, V> segmentedLRU = getSegmentedLRU(key);
        Node<K, V>[] removedNodeWrapper = new Node[1];
        boolean[] executedWrapper = new boolean[1];
        node = map.computeIfAbsent(key, (_k) -> {
            executedWrapper[0] = true;
            Node<K, V> newNode = new Node<>(key, loadValueFunction.apply(key), System.currentTimeMillis() +
                    (minExpireMillis == maxExpireMillis ? minExpireMillis : ThreadLocalRandom.current().nextLong(minExpireMillis, maxExpireMillis)));
            removedNodeWrapper[0] = segmentedLRU.addNodeAndRemoveTailIfFull(newNode);
            return newNode;
        });
        if (removedNodeWrapper[0] != null) {
            map.remove(removedNodeWrapper[0].key, removedNodeWrapper[0]);
        }
        if (!executedWrapper[0]) {
            segmentedLRU.updateNode(node);
            hitCount.increment();
        }
        return node.value;
    }

    @Override
    public V getIfPresent(K key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        Node<K, V> node = getNodeIfPresent(key);
        return node == null ? null : node.value;
    }

    @Override
    public void put(K key, V value, long minExpireMillis, long maxExpireMillis) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (minExpireMillis < 0 || maxExpireMillis < 0) {
            throw new IllegalArgumentException("minExpireMillis and maxExpireMillis must be greater than 0");
        }
        if (maxExpireMillis < minExpireMillis) {
            throw new IllegalArgumentException("maxExpireMillis must be greater than minExpireMillis");
        }
        SegmentedLRU<K, V> segmentedLRU = getSegmentedLRU(key);
        Node<K, V> newNode = new Node<>(key, value, System.currentTimeMillis() +
                (minExpireMillis == maxExpireMillis ? minExpireMillis : ThreadLocalRandom.current().nextLong(minExpireMillis, maxExpireMillis)));
        Node<K, V> removedNode = segmentedLRU.addNodeAndRemoveTailIfFull(newNode);
        if (removedNode != null) {
            map.remove(removedNode.key, removedNode);
        }
        map.put(key, newNode);
    }

    @Override
    public void remove(K key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        map.remove(key);
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

    static class SegmentedLRU<K, V> {
        final LinkedQueue<K, V> hotQueue;
        final LinkedQueue<K, V> coldQueue;

        SegmentedLRU(int hotQueueCapacity, int coldQueueCapacity) {
            this.hotQueue = new LinkedQueue<>(hotQueueCapacity);
            this.coldQueue = new LinkedQueue<>(coldQueueCapacity);
        }

        Node<K, V> addNodeAndRemoveTailIfFull(Node<K, V> node) {
            if (node.status != NodeStatus.INITIAL) {
                throw new RuntimeException("node status is not initial");
            }
            synchronized (this) {
                if (node.status != NodeStatus.INITIAL) {
                    throw new RuntimeException("node status is not initial");
                }
                node.status = NodeStatus.COLD;
                node = coldQueue.addNodeToHeadAndRemoveTailIfFull(node);
                if (node != null) {
                    node.status = NodeStatus.REMOVED;
                }
                return node;
            }
        }

        void updateNode(Node<K, V> node) {
            if (node.status == NodeStatus.INITIAL) {
                throw new RuntimeException("node status is initial");
            }
            if (node.status == NodeStatus.HOT || node.status == NodeStatus.REMOVED) {
                return;
            }
            synchronized (this) {
                if (node.status == NodeStatus.INITIAL) {
                    throw new RuntimeException("node status is initial");
                }
                if (node.status == NodeStatus.HOT || node.status == NodeStatus.REMOVED) {
                    return;
                }
                node = coldQueue.removeNode(node);
                if (node == null) {
                    throw new RuntimeException("node is null");
                }
                node.status = NodeStatus.HOT;
                node = hotQueue.addNodeToHeadAndRemoveTailIfFull(node);
                if (node == null) {
                    return;
                }
                node.status = NodeStatus.COLD;
                node = coldQueue.addNodeToHeadAndRemoveTailIfFull(node);
            }
            if (node != null) {
                throw new RuntimeException("node is not null");
            }
        }
    }

    static class LinkedQueue<K, V> {
        final int capacity;
        int size = 0;
        Node<K, V> head;
        Node<K, V> tail;

        LinkedQueue(int capacity) {
            this.capacity = capacity;
        }

        Node<K, V> addNodeToHeadAndRemoveTailIfFull(Node<K, V> node) {
            if (size == 0) {
                head = node;
                tail = node;
            } else {
                head.prev = node;
                node.next = head;
                head = node;
            }
            size++;
            if (size > capacity) {
                return removeNode(tail);
            }
            return null;
        }

        Node<K, V> removeNode(Node<K, V> node) {
            if (node == head) {
                head = node.next;
            } else {
                node.prev.next = node.next;
            }
            if (node == tail) {
                tail = node.prev;
            } else {
                node.next.prev = node.prev;
            }
            node.prev = null;
            node.next = null;
            size--;
            return node;
        }
    }

    static class Node<K, V> {
        final K key;
        final V value;
        final long expireTime;
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
