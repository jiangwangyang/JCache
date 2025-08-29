package com.github.jiangwangyang.jcache;

public final class CacheStats {

    private final int size;
    private final long startTime;
    private final long recordTime;
    private final long requestCount;
    private final long hitCount;

    CacheStats(int size, long startTime, long recordTime, long requestCount, long hitCount) {
        this.size = size;
        this.startTime = startTime;
        this.recordTime = recordTime;
        this.requestCount = requestCount;
        this.hitCount = hitCount;
    }

    public int size() {
        return size;
    }

    public long startTime() {
        return startTime;
    }

    public long recordTime() {
        return recordTime;
    }

    public long requestCount() {
        return requestCount;
    }

    public long hitCount() {
        return hitCount;
    }

    public long missCount() {
        return requestCount - hitCount;
    }

    public double requestPerSecond() {
        return (double) (requestCount * 1000L) / (recordTime - startTime);
    }

    public double hitPerSecond() {
        return (double) (hitCount * 1000L) / (recordTime - startTime);
    }

    public double missPerSecond() {
        return (double) (missCount() * 1000L) / (recordTime - startTime);
    }

    public double hitRate() {
        return (double) hitCount / requestCount;
    }

    public double missRate() {
        return (double) missCount() / requestCount;
    }

    public CacheStats minus(CacheStats cacheStats) {
        if (startTime != cacheStats.startTime) {
            throw new RuntimeException("startTime not equal");
        }
        return new CacheStats(
                size - cacheStats.size,
                cacheStats.recordTime,
                recordTime,
                requestCount - cacheStats.requestCount,
                hitCount - cacheStats.hitCount);
    }

    public CacheStats plus(CacheStats cacheStats) {
        if (startTime != cacheStats.startTime) {
            throw new RuntimeException("startTime not equal");
        }
        return new CacheStats(
                size + cacheStats.size,
                startTime,
                Math.max(recordTime, cacheStats.recordTime),
                requestCount + cacheStats.requestCount,
                hitCount + cacheStats.hitCount);
    }

}
