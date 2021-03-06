package net.dongliu.direct.utils;

import net.dongliu.direct.allocator.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;
import sun.misc.VM;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * @author Dong Liu dongliu@live.cn
 */
@SuppressWarnings("restriction")
public class UNSAFE {

    private static final Logger logger = LoggerFactory.getLogger(UNSAFE.class);

    private static final Unsafe unsafe;
    private static final long ADDRESS_FIELD_OFFSET;
    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;

    private static final long ARRAY_BASE_OFFSET;
    private static final int PAGE_SIZE;

    private static final boolean PA = VM.isDirectMemoryPageAligned();


    /**
     * {@code true} if and only if the platform supports unaligned access.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Segmentation_fault#Bus_error">Wikipedia on segfault</a>
     */
    private static final boolean UNALIGNED_ACCESS;
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    static {
        ByteBuffer direct = ByteBuffer.allocateDirect(1);
        Field addressField;
        try {
            addressField = Buffer.class.getDeclaredField("address");
            addressField.setAccessible(true);
            if (addressField.getLong(ByteBuffer.allocate(1)) != 0) {
                // A heap buffer must have 0 address.
                addressField = null;
            } else {
                if (addressField.getLong(direct) == 0) {
                    // A direct buffer must have non-zero address.
                    addressField = null;
                }
            }
        } catch (Throwable t) {
            // Failed to access the address field.
            addressField = null;
        }

        if (addressField == null) {
            throw new RuntimeException("ByteBuffer addressField not found");
        }

        Unsafe _unsafe;
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            _unsafe = (Unsafe) unsafeField.get(null);

            // Ensure the unsafe supports all necessary methods to work around the mistake in the latest OpenJDK.
            // https://github.com/netty/netty/issues/1061
            // http://www.mail-archive.com/jdk6-dev@openjdk.java.net/msg00698.html
            try {
                if (_unsafe != null) {
                    _unsafe.getClass().getDeclaredMethod(
                            "copyMemory", Object.class, long.class, Object.class, long.class, long.class);
                    logger.debug("sun.misc.Unsafe.copyMemory: available");
                }
            } catch (NoSuchMethodError | NoSuchMethodException t) {
                logger.debug("sun.misc.Unsafe.copyMemory: unavailable");
                throw t;
            }
        } catch (Throwable cause) {
            // Unsafe.copyMemory(Object, long, Object, long, long) unavailable.
            _unsafe = null;
        }

        if (_unsafe == null) {
            throw new RuntimeException("Unsafe not available");
        }
        unsafe = _unsafe;

        ADDRESS_FIELD_OFFSET = objectFieldOffset(addressField);
        boolean unalignedAccess;
        try {
            Class<?> bitsClass = Class.forName("java.nio.Bits", false, ClassLoader.getSystemClassLoader());
            Method unalignedMethod = bitsClass.getDeclaredMethod("unaligned");
            unalignedMethod.setAccessible(true);
            unalignedAccess = Boolean.TRUE.equals(unalignedMethod.invoke(null));

            Method psMethod = bitsClass.getDeclaredMethod("pageSize");
            psMethod.setAccessible(true);
            PAGE_SIZE = (Integer) psMethod.invoke(null);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        UNALIGNED_ACCESS = unalignedAccess;
        logger.debug("java.nio.Bits.unaligned: {}", UNALIGNED_ACCESS);

        ARRAY_BASE_OFFSET = arrayBaseOffset();
    }

    static void throwException(Throwable t) {
        unsafe.throwException(t);
    }


    /**
     * Returns the value of the Java system property with the specified
     * {@code key}, while falling back to the specified default value if
     * the property access fails.
     *
     * @return the property value.
     * {@code def} if there's no such property or if an access to the
     * specified property is not allowed.
     */
    public static String getSystemProperty(final String key, String def) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty.");
        }

        String value = null;
        try {
            if (System.getSecurityManager() == null) {
                value = System.getProperty(key);
            } else {
                value = AccessController.doPrivileged(new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return System.getProperty(key);
                    }
                });
            }
        } catch (Exception ignore) {
        }

        if (value == null) {
            return def;
        }

        return value;
    }

    public static long directBufferAddress(ByteBuffer buffer) {
        return getLong(buffer, ADDRESS_FIELD_OFFSET);
    }

    static long arrayBaseOffset() {
        return unsafe.arrayBaseOffset(byte[].class);
    }

    static Object getObject(Object object, long fieldOffset) {
        return unsafe.getObject(object, fieldOffset);
    }

    static Object getObjectVolatile(Object object, long fieldOffset) {
        return unsafe.getObjectVolatile(object, fieldOffset);
    }

    static int getInt(Object object, long fieldOffset) {
        return unsafe.getInt(object, fieldOffset);
    }

    private static long getLong(Object object, long fieldOffset) {
        return unsafe.getLong(object, fieldOffset);
    }

    static long objectFieldOffset(Field field) {
        return unsafe.objectFieldOffset(field);
    }

    public static byte getByte(long address) {
        return unsafe.getByte(address);
    }

    static short getShort(long address) {
        if (UNALIGNED_ACCESS) {
            return unsafe.getShort(address);
        } else if (BIG_ENDIAN) {
            return (short) (getByte(address) << 8 | getByte(address + 1) & 0xff);
        } else {
            return (short) (getByte(address + 1) << 8 | getByte(address) & 0xff);
        }
    }

    static int getInt(long address) {
        if (UNALIGNED_ACCESS) {
            return unsafe.getInt(address);
        } else if (BIG_ENDIAN) {
            return getByte(address) << 24 |
                    (getByte(address + 1) & 0xff) << 16 |
                    (getByte(address + 2) & 0xff) << 8 |
                    getByte(address + 3) & 0xff;
        } else {
            return getByte(address + 3) << 24 |
                    (getByte(address + 2) & 0xff) << 16 |
                    (getByte(address + 1) & 0xff) << 8 |
                    getByte(address) & 0xff;
        }
    }

    static long getLong(long address) {
        if (UNALIGNED_ACCESS) {
            return unsafe.getLong(address);
        } else if (BIG_ENDIAN) {
            return (long) getByte(address) << 56 |
                    ((long) getByte(address + 1) & 0xff) << 48 |
                    ((long) getByte(address + 2) & 0xff) << 40 |
                    ((long) getByte(address + 3) & 0xff) << 32 |
                    ((long) getByte(address + 4) & 0xff) << 24 |
                    ((long) getByte(address + 5) & 0xff) << 16 |
                    ((long) getByte(address + 6) & 0xff) << 8 |
                    (long) getByte(address + 7) & 0xff;
        } else {
            return (long) getByte(address + 7) << 56 |
                    ((long) getByte(address + 6) & 0xff) << 48 |
                    ((long) getByte(address + 5) & 0xff) << 40 |
                    ((long) getByte(address + 4) & 0xff) << 32 |
                    ((long) getByte(address + 3) & 0xff) << 24 |
                    ((long) getByte(address + 2) & 0xff) << 16 |
                    ((long) getByte(address + 1) & 0xff) << 8 |
                    (long) getByte(address) & 0xff;
        }
    }

    static void putOrderedObject(Object object, long address, Object value) {
        unsafe.putOrderedObject(object, address, value);
    }

    static void putByte(long address, byte value) {
        unsafe.putByte(address, value);
    }

    static void putShort(long address, short value) {
        if (UNALIGNED_ACCESS) {
            unsafe.putShort(address, value);
        } else if (BIG_ENDIAN) {
            putByte(address, (byte) (value >>> 8));
            putByte(address + 1, (byte) value);
        } else {
            putByte(address + 1, (byte) (value >>> 8));
            putByte(address, (byte) value);
        }
    }

    static void putInt(long address, int value) {
        if (UNALIGNED_ACCESS) {
            unsafe.putInt(address, value);
        } else if (BIG_ENDIAN) {
            putByte(address, (byte) (value >>> 24));
            putByte(address + 1, (byte) (value >>> 16));
            putByte(address + 2, (byte) (value >>> 8));
            putByte(address + 3, (byte) value);
        } else {
            putByte(address + 3, (byte) (value >>> 24));
            putByte(address + 2, (byte) (value >>> 16));
            putByte(address + 1, (byte) (value >>> 8));
            putByte(address, (byte) value);
        }
    }

    static void putLong(long address, long value) {
        if (UNALIGNED_ACCESS) {
            unsafe.putLong(address, value);
        } else if (BIG_ENDIAN) {
            putByte(address, (byte) (value >>> 56));
            putByte(address + 1, (byte) (value >>> 48));
            putByte(address + 2, (byte) (value >>> 40));
            putByte(address + 3, (byte) (value >>> 32));
            putByte(address + 4, (byte) (value >>> 24));
            putByte(address + 5, (byte) (value >>> 16));
            putByte(address + 6, (byte) (value >>> 8));
            putByte(address + 7, (byte) value);
        } else {
            putByte(address + 7, (byte) (value >>> 56));
            putByte(address + 6, (byte) (value >>> 48));
            putByte(address + 5, (byte) (value >>> 40));
            putByte(address + 4, (byte) (value >>> 32));
            putByte(address + 3, (byte) (value >>> 24));
            putByte(address + 2, (byte) (value >>> 16));
            putByte(address + 1, (byte) (value >>> 8));
            putByte(address, (byte) value);
        }
    }

    public static void copyMemory(long srcAddr, long dstAddr, long length) {
        //u.copyMemory(srcAddr, dstAddr, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            unsafe.copyMemory(srcAddr, dstAddr, size);
            length -= size;
            srcAddr += size;
            dstAddr += size;
        }
    }

    static void copyMemory(Object src, long srcOffset, Object dst, long dstOffset, long length) {
        //u.copyMemory(src, srcOffset, dst, dstOffset, length);
        while (length > 0) {
            long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
            unsafe.copyMemory(src, srcOffset, dst, dstOffset, size);
            length -= size;
            srcOffset += size;
            dstOffset += size;
        }
    }

    public static ClassLoader getClassLoader(final Class<?> clazz) {
        if (System.getSecurityManager() == null) {
            return clazz.getClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return clazz.getClassLoader();
                }
            });
        }
    }

    public static ClassLoader getContextClassLoader() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
    }

    public static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return ClassLoader.getSystemClassLoader();
                }
            });
        }
    }

    public static int addressSize() {
        return unsafe.addressSize();
    }

    public static long allocateMemory(long size) {
        return unsafe.allocateMemory(size);
    }

    public static void freeMemory(long address) {
        unsafe.freeMemory(address);
    }

    public static void copyMemory(long srcAddr, byte[] dst, int dstIndex, long length) {
        copyMemory(null, srcAddr, dst, ARRAY_BASE_OFFSET + dstIndex, length);
    }

    public static void copyMemory(byte[] src, int srcIndex, long dstAddr, long length) {
        copyMemory(src, ARRAY_BASE_OFFSET + srcIndex, null, dstAddr, length);
    }

    public static Memory allocateMemory(int cap) {
        long size = Math.max(1L, (long) cap + (PA ? PAGE_SIZE : 0));

        long base = allocateMemory(size);
        long address;
        if (PA && (base % PAGE_SIZE != 0)) {
            // Round up to page boundary
            address = base + PAGE_SIZE - (base & (PAGE_SIZE - 1));
        } else {
            address = base;
        }
        return new Memory(address, cap);
    }

    public static void freeMemory(Memory memory) {
        freeMemory(memory.getAddress());
    }

    public static Unsafe getUnsafe() {
        return unsafe;
    }
}
