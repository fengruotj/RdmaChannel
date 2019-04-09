package com.basic.rdmachannel.write;


import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.write.RdmaWriteServer
 */
public class RdmaWriteServer implements RdmaConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(RdmaWriteServer.class);
    private static CyclicBarrier cyclicBarrier=new CyclicBarrier(2);
    private static RdmaChannel clientChannel;

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaServer=new RdmaNode("10.10.0.25",1955, new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RDMA_WRITE_RESPONDER);
        rdmaServer.bindConnectCompleteListener(new RdmaWriteServer());

        cyclicBarrier.await();
        cyclicBarrier.reset();

        RdmaBufferManager rdmaBufferManager = rdmaServer.getRdmaBufferManager();
        RdmaBuffer rdmaData = rdmaBufferManager.get(4096);
        ByteBuffer dataBuffer = rdmaData.getByteBuffer();

        RdmaBuffer rdmaSend = rdmaBufferManager.get(1024);
        ByteBuffer sendBuffer = rdmaSend.getByteBuffer();
        sendBuffer.putLong(rdmaData.getAddress());
        sendBuffer.putInt(rdmaData.getLkey());
        sendBuffer.putInt(rdmaData.getLength());

        clientChannel.rdmaSendInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer IMM) {
                System.out.println("SEND Success!!!");
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
        },new long[]{rdmaSend.getAddress()},new int[]{rdmaSend.getLength()},new int[]{rdmaSend.getLkey()});

        cyclicBarrier.await();

        Thread.sleep(5000);
        logger.info("RdmaWriteServer receive msg from client: "+dataBuffer.asCharBuffer().toString());
        Thread.sleep(Integer.MAX_VALUE);
    }

    @Override
    public void onSuccess(RdmaChannel rdmaChannel) {
        logger.info("success accept RdmaChannel");
        logger.info(rdmaChannel.toString());
        clientChannel=rdmaChannel;
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
        exception.printStackTrace();
    }
}
