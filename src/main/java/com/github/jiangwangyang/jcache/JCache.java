package com.github.jiangwangyang.jcache;

import java.util.function.Function;

public interface JCache<K, V> {

    V get(K key, Function<K, V> loadValueFunction, long minExpireMillis, long maxExpireMillis);

    V getIfPresent(K key);

    void put(K key, V value, long minExpireMillis, long maxExpireMillis);

    void remove(K key);

    CacheStats stats();
}
