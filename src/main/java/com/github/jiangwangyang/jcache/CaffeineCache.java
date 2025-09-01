package com.github.jiangwangyang.jcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class CaffeineCache<K, V> implements JCache<K, V> {

    final Cache<K, ValueWrapper<V>> caffeineCache;
    final long startMillis;

    public CaffeineCache(int capacity) {
        assert capacity > 0;
        this.caffeineCache = Caffeine.newBuilder()
                .initialCapacity(capacity)
                .maximumSize(capacity)
                .recordStats()
                .expireAfter(new Expiry<K, ValueWrapper<V>>() {
                    @Override
                    public long expireAfterCreate(K key, ValueWrapper<V> valueWrapper, long currentTime) {
                        return Duration.ofMillis(valueWrapper.expireMillis).toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(K key, ValueWrapper<V> valueWrapper, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(K key, ValueWrapper<V> valueWrapper, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
        startMillis = System.currentTimeMillis();
    }

    @Override
    public V get(K key, Function<K, V> loadValueFunction, long minExpireMillis, long maxExpireMillis) {
        assert key != null;
        assert loadValueFunction != null;
        assert minExpireMillis >= 0;
        assert maxExpireMillis >= 0;
        assert minExpireMillis <= maxExpireMillis;
        return caffeineCache.get(key, k -> {
            long expireMillis = ThreadLocalRandom.current().nextLong(minExpireMillis, maxExpireMillis + 1);
            return new ValueWrapper<>(loadValueFunction.apply(k), expireMillis);
        }).value;
    }

    @Override
    public V getIfPresent(K key) {
        assert key != null;
        ValueWrapper<V> valueWrapper = caffeineCache.getIfPresent(key);
        return valueWrapper == null ? null : valueWrapper.value;
    }

    @Override
    public void put(K key, V value, long minExpireMillis, long maxExpireMillis) {
        assert key != null;
        assert minExpireMillis >= 0;
        assert maxExpireMillis >= 0;
        assert minExpireMillis <= maxExpireMillis;
        long expireMillis = ThreadLocalRandom.current().nextLong(minExpireMillis, maxExpireMillis + 1);
        caffeineCache.put(key, new ValueWrapper<>(value, expireMillis));
    }

    @Override
    public void remove(K key) {
        assert key != null;
        caffeineCache.invalidate(key);
    }

    @Override
    public CacheStats stats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = caffeineCache.stats();
        return new CacheStats((int) caffeineCache.estimatedSize(), startMillis, System.currentTimeMillis(), stats.requestCount(), stats.hitCount());
    }

    static class ValueWrapper<V> {
        final V value;
        final long expireMillis;

        ValueWrapper(V value, long expireMillis) {
            this.value = value;
            this.expireMillis = expireMillis;
        }
    }
}
