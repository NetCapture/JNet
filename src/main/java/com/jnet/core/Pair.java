package com.jnet.core;

/**
 * 键值对工具类
 * 替代 ff.jnezha.jnt.utils.Pair
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class Pair<K, V> {
    public final K key;
    public final V value;

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public static <K, V> Pair<K, V> of(K key, V value) {
        return new Pair<>(key, value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
