package com.yang.ratingsystem.manager.cache;

import cn.hutool.core.util.HashUtil;
import lombok.Data;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @Author 小小星仔
 * @Create 2025-04-17 23:09
 */
public class HeavyKeeper implements TopK {
    private static final int LOOKUP_TABLE_SIZE = 256;
    private final int k;
    private final int width;
    private final int depth;
    private final double[] lookupTable;
    private final Bucket[][] buckets;
    private final PriorityQueue<Node> minHeap;
    private final BlockingQueue<Item> expelledQueue;
    private final Random random;
    private long total;
    private final int minCount;
    private final long ttlMillis; // 新增TTL字段
    private final Map<String, Long> keyExpiryMap = new ConcurrentHashMap<>();

    public HeavyKeeper(int k, int width, int depth, double decay, int minCount, long ttl, TimeUnit unit) {
        this.k = k;
        this.width = width;
        this.depth = depth;
        this.minCount = minCount;

        this.lookupTable = new double[LOOKUP_TABLE_SIZE];
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {
            lookupTable[i] = Math.pow(decay, i);
        }

        this.buckets = new Bucket[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                buckets[i][j] = new Bucket();
            }
        }

        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
        this.expelledQueue = new LinkedBlockingQueue<>();
        this.random = new Random();
        this.total = 0;
        this.ttlMillis = unit.toMillis(ttl);
    }

    @Override
    public AddResult add(String key, int increment) {
        // 检查并清理过期key
        cleanExpiredKeys();
        byte[] keyBytes = key.getBytes();
        long itemFingerprint = hash(keyBytes);
        int maxCount = 0;

        for (int i = 0; i < depth; i++) {
            int bucketNumber = Math.abs(hash(keyBytes)) % width;
            Bucket bucket = buckets[i][bucketNumber];

            synchronized (bucket) {
                if (bucket.count == 0) {
                    bucket.fingerprint = itemFingerprint;
                    bucket.count = increment;
                    maxCount = Math.max(maxCount, increment);
                } else if (bucket.fingerprint == itemFingerprint) {
                    bucket.count += increment;
                    maxCount = Math.max(maxCount, bucket.count);
                } else {
                    for (int j = 0; j < increment; j++) {
                        double decay = bucket.count < LOOKUP_TABLE_SIZE ?
                                lookupTable[bucket.count] :
                                lookupTable[LOOKUP_TABLE_SIZE - 1];
                        if (random.nextDouble() < decay) {
                            bucket.count--;
                            if (bucket.count == 0) {
                                bucket.fingerprint = itemFingerprint;
                                bucket.count = increment - j;
                                maxCount = Math.max(maxCount, bucket.count);
                                break;
                            }
                        }
                    }
                }
            }
        }

        total += increment;

        if (maxCount < minCount) {
            return new AddResult(null, false, null);
        }

        synchronized (minHeap) {
            boolean isHot = false;
            String expelled = null;
            AddResult result = null; // 添加result变量声明

            Optional<Node> existing = minHeap.stream()
                    .filter(n -> n.key.equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                minHeap.remove(existing.get());
                minHeap.add(new Node(key, maxCount));
                isHot = true;
                result = new AddResult(null, isHot, key); // 创建result对象
            } else {
                if (minHeap.size() < k || maxCount >= Objects.requireNonNull(minHeap.peek()).count) {
                    Node newNode = new Node(key, maxCount);
                    if (minHeap.size() >= k) {
                        expelled = minHeap.poll().key;
                        expelledQueue.offer(new Item(expelled, maxCount));
                    }
                    minHeap.add(newNode);
                    isHot = true;
                    result = new AddResult(expelled, isHot, key); // 创建result对象
                } else {
                    result = new AddResult(null, false, key); // 处理不进入TopK的情况
                }
            }

            // 更新或设置key的过期时间
            keyExpiryMap.put(key, System.currentTimeMillis() + ttlMillis);
            return result; // 返回正确的结果
        }
        
        // 更新或设置key的过期时间
        keyExpiryMap.put(key, System.currentTimeMillis() + ttlMillis);
        return result;
    }

    @Override
    public List<Item> list() {
        cleanExpiredKeys();
        synchronized (minHeap) {
            List<Item> result = new ArrayList<>(minHeap.size());
            for (Node node : minHeap) {
                result.add(new Item(node.key, node.count));
            }
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return result;
        }
    }

    @Override
    public BlockingQueue<Item> expelled() {
        return expelledQueue;
    }

    @Override
    public void fading() {
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {
                synchronized (bucket) {
                    bucket.count = bucket.count >> 1;
                }
            }
        }

        synchronized (minHeap) {
            PriorityQueue<Node> newHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
            for (Node node : minHeap) {
                newHeap.add(new Node(node.key, node.count >> 1));
            }
            minHeap.clear();
            minHeap.addAll(newHeap);
        }

        total = total >> 1;
    }

    @Override
    public long total() {
        return total;
    }

    private static class Bucket {
        long fingerprint;
        int count;
    }

    private static class Node {
        final String key;
        final int count;

        Node(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    private static int hash(byte[] data) {
        return HashUtil.murmur32(data);
    }

    private void cleanExpiredKeys() {
        long now = System.currentTimeMillis();
        keyExpiryMap.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                // 从所有数据结构中移除过期key
                removeKey(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void removeKey(String key) {
        // 1. 从minHeap中移除
        synchronized (minHeap) {
            minHeap.removeIf(node -> node.key.equals(key));
        }
        
        // 2. 从buckets中移除
        byte[] keyBytes = key.getBytes();
        long fingerprint = hash(keyBytes);
        for (int i = 0; i < depth; i++) {
            int bucketNumber = Math.abs(hash(keyBytes)) % width;
            Bucket bucket = buckets[i][bucketNumber];
            synchronized (bucket) {
                if (bucket.fingerprint == fingerprint) {
                    bucket.count = 0;
                    bucket.fingerprint = 0;
                }
            }
        }
        
        // 3. 从expelledQueue中移除
        expelledQueue.removeIf(item -> item.key().equals(key));
        
        // 4. 从keyExpiryMap中移除
        keyExpiryMap.remove(key);
    }

}

// 新增返回结果类
@Data
class AddResult {
    // 被挤出的 key
    private final String expelledKey;
    // 当前 key 是否进入 TopK
    private final boolean isHotKey;
    // 当前操作的 key
    private final String currentKey;

    public AddResult(String expelledKey, boolean isHotKey, String currentKey) {
        this.expelledKey = expelledKey;
        this.isHotKey = isHotKey;
        this.currentKey = currentKey;
    }

}
