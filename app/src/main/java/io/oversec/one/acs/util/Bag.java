package io.oversec.one.acs.util;

public class Bag<T> {

    private T[] data;
    private int manyItems;

    public Bag(int initialCapacity) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException
                    ("The initialCapacity is negative: " + initialCapacity);
        //noinspection unchecked
        data = (T[]) new Object[initialCapacity];
        manyItems = 0;
    }

    public void add(T element) {
        if (manyItems == data.length) {  // Ensure twice as much space as we need.
            ensureCapacity((manyItems + 1) * 2);
        }

        data[manyItems] = element;
        manyItems++;
    }

    public void ensureCapacity(int minimumCapacity) {
        T[] biggerArray;

        if (data.length < minimumCapacity) {
            //noinspection unchecked
            biggerArray = (T[]) new Object[minimumCapacity];
            System.arraycopy(data, 0, biggerArray, 0, manyItems);
            data = biggerArray;
        }
    }

    public int size() {
        return manyItems;
    }


    public T get(int idx) {
        return data[idx];
    }

    public boolean remove(T target) {
        int index; // The location of target in the data array.

        // Find the first occurrence of the target in the bag.
        index = 0;
        while ((index < manyItems) && (target != data[index]))
            index++;

        if (index == manyItems)
            // The target was not found, so nothing is removed.
            return false;
        else {  // The target was found at data[index].
            // So reduce manyItems by 1 and copy the last element onto data[index].
            manyItems--;
            data[index] = data[manyItems];
            data[manyItems] = null;
            return true;
        }
    }
}
