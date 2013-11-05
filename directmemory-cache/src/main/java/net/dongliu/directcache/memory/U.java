package net.dongliu.directcache.memory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 *
 * Wrapper Unsafe Operations.
 * @author: dongliu
 */
public class U {

    private static final sun.misc.Unsafe UNSAFE;

    static {
        Object result = null;
        try {
            Class<?> clazz = Class.forName("sun.misc.Unsafe");
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() == clazz && (field.getModifiers() & (Modifier.FINAL | Modifier.STATIC))
                        == (Modifier.FINAL | Modifier.STATIC)) {
                    field.setAccessible(true);
                    result = field.get(null);
                    break;
                }
            }
        } catch (Throwable ignore) {}
        UNSAFE = (sun.misc.Unsafe) result;
        if (UNSAFE == null) {
            throw new UnsupportedOperationException("U not found.Should used on open jdk / oracle jdk");
        }
    }

    /**
     * Allocates a new block of native memory, of the given size in bytes.
     * The resulting native pointer will never be zero, and will be aligned for all value types.
     */
    public static long allocateMemory(long size) {
        return UNSAFE.allocateMemory(size);
    }

    /**
     * Resizes a new block of native memory, to the given size in bytes.
     * The resulting native pointer will be zero if and only if the requested size is zero.
     * The resulting native pointer will be aligned for all value types.
     * The address passed to this method may be null, in which case an allocation will be performed.
     */
    public static long reallocateMemory(long address, long size) {
        return UNSAFE.reallocateMemory(address, size);
    }

    /**
     * Disposes of a block of native memory, as obtained from #allocateMemory  or #reallocateMemory .
     * The address passed to this method may be null, in which case no action is taken.
     */
    public static void freeMemory(long l) {
            UNSAFE.freeMemory(l);
    }

    /**
     * write bytes to unsafe memory.
     */
    public static void write(long address, byte[] src, int offset, int size) {
        UNSAFE.copyMemory(src, UNSAFE.arrayBaseOffset(byte[].class) + offset, null, address, size);
    }

    /**
     * read bytes from unsafe memory.
     */
    public static void read(long address, byte[] src, int offset, int size) {
        UNSAFE.copyMemory(null, address, src, UNSAFE.arrayBaseOffset(byte[].class) + offset,size);
    }
}
