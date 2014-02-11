package net.dongliu.direct.memory;

import net.dongliu.direct.exception.AllocatorException;

/**
 * Allocator
 *
 * @author dongliu
 */
public interface Allocator {

    /**
     * allocate memory buf.
     *
     * @param size
     * @return the buf allocated. null if cannot allocate memory due to not enough free memory.
     * @throws AllocatorException if other exception happened.
     */
    MemoryBuffer allocate(final int size);

    /**
     * destroy allocator, release all resources.
     * after destroy this cannot be actualUsed any more
     */
    void destroy();

    /**
     * the capacity
     *
     * @return
     */
    long getCapacity();

    /**
     * the memory size actualUsed.
     *
     * @return
     */
    long actualUsed();
}