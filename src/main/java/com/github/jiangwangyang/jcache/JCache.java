package com.github.jiangwangyang.jcache;

import java.util.function.Function;

public interface JCache<K, V> {

    V get(K key, long minExpireMillis, long maxExpireMillis, Function<K, V> loadValueFunction);

    V getIfPresent(K key);

    void put(K key, V value, long minExpireMillis, long maxExpireMillis);

    void remove(K key);

    CacheStats stats();
}
