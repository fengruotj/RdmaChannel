package com.basic.rdmachannel.write;

import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.write.RdmaWriteClient
 */
public class RdmaWriteClient {
    private static final Logger logger = LoggerFactory.getLogger(RdmaWriteClient.class);
    private static CyclicBarrier cyclicBarrier=new CyclicBarrier(2);

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaClient=new RdmaNode("10.10.0.24", true, new RdmaChannelConf(), RdmaChannel.RdmaChannelType.RDMA_WRITE_REQUESTOR, new RdmaConnectListener() {
            @Override
            public void onSuccess(RdmaChannel rdmaChannel) {
                logger.info("success connect");
            }

            @Override
            public void onFailure(Throwable exception) {
                exception.printStackTrace();
            }
        });

        RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress("10.10.0.25", 1955), true, RdmaChannel.RdmaChannelType.RDMA_READ_RESPONDER);

        RdmaBufferManager rdmaBufferManager = rdmaClient.getRdmaBufferManager();
        RdmaBuffer rdmaBuffer = rdmaBufferManager.get(1024);
        ByteBuffer byteBuffer = rdmaBuffer.getByteBuffer();

        RdmaBuffer rdmaData = rdmaBufferManager.get(4096);
        ByteBuffer dataBuffer = rdmaData.getByteBuffer();
        String str="Hello! I am Server!";
        dataBuffer.asCharBuffer().put(str);
        dataBuffer.flip();

        rdmaChannel.rdmaReceiveInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer IMM) {
                logger.info("success excute receive request!");
                try {
                    cyclicBarrier.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Throwable exception) {

            }
        },rdmaBuffer.getAddress(),rdmaBuffer.getLength(),rdmaBuffer.getLkey());

        cyclicBarrier.await();
        long remoteAddress = byteBuffer.getLong();
        int rkey = byteBuffer.getInt();
        int rlength = byteBuffer.getInt();
        rdmaChannel.rdmaWriteInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer IMM) {
                logger.info("success excute write request!");
            }

            @Override
            public void onFailure(Throwable exception) {

            }
        },rdmaData.getAddress(),rdmaData.getLength(),rdmaData.getLkey(),remoteAddress,rkey);

        Thread.sleep(Integer.MAX_VALUE);
    }
}
