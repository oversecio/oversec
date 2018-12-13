package io.oversec.one.acs.util;

public class SynchronizedPool<T> extends SimplePool<T> {
    private final Object mLock = new Object();

    /**
     * Creates a new instance.
     *
     * @param maxPoolSize The max pool size.
     * @throws IllegalArgumentException If the max pool size is less than zero.
     */
    public SynchronizedPool(int maxPoolSize) {
        super(maxPoolSize);
    }

    @Override
    public T acquire() {
        synchronized (mLock) {
            return super.acquire();
        }
    }

    @Override
    public boolean release(T element) {
        synchronized (mLock) {
            return super.release(element);
        }
    }
}