package com.ibm.disni.channel;

import com.ibm.disni.rdma.verbs.IbvMr;
import com.ibm.disni.rdma.verbs.IbvPd;
import com.ibm.disni.rdma.verbs.SVCRegMr;
import com.ibm.disni.unsafe.memory.MemoryBlock;
import com.ibm.disni.unsafe.memory.UnsafeMemoryAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;

/**
 * locate org.apache.storm.messaging.rdma
 * Created by mastertj on 2018/8/23.
 */
public class RdmaBuffer {
    private static final Logger logger = LoggerFactory.getLogger(RdmaBuffer.class);

    private IbvMr ibvMr = null;
    private final long address;
    private final int length;
    private final MemoryBlock block;

    public static final UnsafeMemoryAllocator unsafeAlloc = new UnsafeMemoryAllocator();

    public RdmaBuffer(IbvPd ibvPd, int length) throws IOException {
        block = unsafeAlloc.allocate((long)length);
        address = block.getBaseOffset();
        this.length = length;
        register(ibvPd);
    }

    public long getAddress() {
        return address;
    }
    public int getLength() {
        return length;
    }
    public int getLkey() {
        return ibvMr.getLkey();
    }

    public void free() {
        unregister();
        unsafeAlloc.free(block);
    }

    private void register(IbvPd ibvPd) throws IOException {
        int access = IbvMr.IBV_ACCESS_LOCAL_WRITE | IbvMr.IBV_ACCESS_REMOTE_WRITE |
                IbvMr.IBV_ACCESS_REMOTE_READ;

        SVCRegMr sMr = ibvPd.regMr(getAddress(), getLength(), access).execute();
        ibvMr = sMr.getMr();
        sMr.free();
    }

    private void unregister() {
        if (ibvMr != null) {
            try {
                ibvMr.deregMr().execute().free();
            } catch (IOException e) {
                logger.warn("Deregister MR failed");
            }
            ibvMr = null;
        }
    }

    public ByteBuffer getByteBuffer() throws IOException {
        Class<?> classDirectByteBuffer;
        try {
            classDirectByteBuffer = Class.forName("java.nio.DirectByteBuffer");
        } catch (ClassNotFoundException e) {
            throw new IOException("java.nio.DirectByteBuffer class not found");
        }
        Constructor<?> constructor;
        try {
            constructor = classDirectByteBuffer.getDeclaredConstructor(long.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new IOException("java.nio.DirectByteBuffer constructor not found");
        }
        constructor.setAccessible(true);
        ByteBuffer byteBuffer;
        try {
            byteBuffer = (ByteBuffer)constructor.newInstance(getAddress(), getLength());
        } catch (Exception e) {
            throw new IOException("java.nio.DirectByteBuffer exception: " + e.toString());
        }

        return byteBuffer;
    }
}
