package net.dongliu.direct.memory;

import java.nio.BufferOverflowException;

/**
 * a memory area.
 *
 * @author dongliu
 */
public abstract class MemoryBuffer {

    /**
     * size actual actualUsed
     */
    private volatile int size;

    protected MemoryBuffer() {
    }

    /**
     * write data.
     */
    public void write(byte[] data) {
        if (isDispose()) {
            throw new IllegalStateException("memory has been disposed");
        }
        if (data.length > getCapacity()) {
            throw new BufferOverflowException();
        }
        this.size = data.length;
        getMemory().write(getOffset(), data);
    }

    /**
     * read all data has been written in.
     */
    public byte[] read() {
        if (isDispose()) {
            throw new IllegalStateException("memory has been disposed");
        }
        byte[] buf = new byte[this.size];
        getMemory().read(getOffset(), buf);
        return buf;
    }

    /**
     * read all data has been written in into buf.
     * we should reuse buf.
     */
    public byte[] read(byte[] buf) {
        if (isDispose()) {
            throw new IllegalStateException("memory has been disposed");
        }
        if (buf.length < this.size) {
            throw new BufferOverflowException();
        }
        getMemory().read(getOffset(), buf, this.size);
        return buf;
    }

    public abstract int getCapacity();

    public int getSize() {
        return this.size;
    }

    public abstract int getOffset();

    public abstract UnsafeMemory getMemory();

    /**
     * mark this buffer as destroyed.
     */
    public void dispose() {
        this.size = -1;
    }

    public boolean isDispose() {
        return this.size == -1;
    }

}