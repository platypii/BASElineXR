package com.platypii.baselinexr.calibration

import com.platypii.baselinexr.measurements.MMagData

/**
 * Thread-safe circular buffer for magnetometer samples.
 * When full, oldest samples are overwritten.
 *
 * @param capacity Maximum number of samples to store
 */
class MagRingBuffer(val capacity: Int) {
    private val buffer = arrayOfNulls<MMagData>(capacity)
    private var head = 0  // Next write position
    private var count = 0 // Current number of elements

    /** Add a sample, overwriting the oldest if full. */
    @Synchronized
    fun add(mag: MMagData) {
        buffer[head] = mag
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /** Return all samples in chronological order (oldest first). */
    @Synchronized
    fun toList(): List<MMagData> {
        if (count == 0) return emptyList()
        val result = ArrayList<MMagData>(count)
        val start = if (count < capacity) 0 else head
        for (i in 0 until count) {
            val idx = (start + i) % capacity
            result.add(buffer[idx]!!)
        }
        return result
    }

    /** Current number of samples in the buffer. */
    @Synchronized
    fun size(): Int = count

    /** Whether the buffer has reached capacity. */
    @Synchronized
    fun isFull(): Boolean = count >= capacity

    /** Clear all samples. */
    @Synchronized
    fun clear() {
        for (i in buffer.indices) buffer[i] = null
        head = 0
        count = 0
    }
}
