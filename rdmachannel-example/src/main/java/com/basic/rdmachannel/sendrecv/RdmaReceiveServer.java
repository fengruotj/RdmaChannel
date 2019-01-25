package com.basic.rdmachannel.sendrecv;


import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.sendrecv.RdmaReceiveServer
 */
public class RdmaReceiveServer implements RdmaConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(RdmaReceiveServer.class);
    private static CountDownLatch countDownLatch=new CountDownLatch(1);
    private static RdmaChannel clientChannel;

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaServer=new RdmaNode("10.10.0.25", false, new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RPC, new RdmaReceiveServer());

        countDownLatch.await();
        RdmaBufferManager rdmaBufferManager = rdmaServer.getRdmaBufferManager();
        RdmaBuffer rdmaBuffer = rdmaBufferManager.get(1024);
        ByteBuffer byteBuffer = rdmaBuffer.getByteBuffer();

        clientChannel.rdmaReceiveInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf) {
                logger.info("success excute receive request!");
                logger.info("RdmaWriteServer receive msg from client: "+byteBuffer.asCharBuffer().toString());
            }

            @Override
            public void onFailure(Throwable exception) {

            }
        },rdmaBuffer.getAddress(),rdmaBuffer.getLength(),rdmaBuffer.getLkey());

        Thread.sleep(Integer.MAX_VALUE);
    }

    @Override
    public void onSuccess(RdmaChannel rdmaChannel) {
        logger.info("success accept RdmaChannel");
        logger.info(rdmaChannel.toString());
        clientChannel=rdmaChannel;
        countDownLatch.countDown();
    }

    @Override
    public void onFailure(Throwable exception) {
        exception.printStackTrace();
    }
}
