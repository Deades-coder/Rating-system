package com.yang.ratingsystem.manager.cache;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * @Author 小小星仔
 * @Create 2025-04-17 23:08
 */
public interface TopK {
    AddResult add(String key, int increment);
    List<Item> list();
    BlockingQueue<Item> expelled();
    void fading();
    long total();
}
