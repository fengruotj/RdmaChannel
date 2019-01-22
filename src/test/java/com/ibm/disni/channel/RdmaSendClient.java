package com.ibm.disni.channel;

import com.ibm.disni.mr.RdmaBuffer;
import com.ibm.disni.mr.RdmaBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmaChannel-1.0-SNAPSHOT-jar-with-dependencies.jar:rdmaChannel-1.0-SNAPSHOT-tests.jar com.ibm.disni.channel.RdmaSendClient
 */
public class RdmaSendClient {
    private static final Logger logger = LoggerFactory.getLogger(RdmaSendClient.class);

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaClient=new RdmaNode("10.10.0.24", true, new RdmaShuffleConf(), new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf) {
                logger.info("success1111");
            }

            @Override
            public void onFailure(Throwable exception) {
                exception.printStackTrace();
            }
        });

        RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress("10.10.0.25", 1955), true, RdmaChannel.RdmaChannelType.RDMA_READ_REQUESTOR);

        RdmaBufferManager rdmaBufferManager = rdmaClient.getRdmaBufferManager();
        RdmaBuffer rdmaBuffer = rdmaBufferManager.get(1024);
        ByteBuffer byteBuffer = rdmaBuffer.getByteBuffer();
        String str="Hello! I am Client!";
        byteBuffer.asCharBuffer().put(str);
        byteBuffer.flip();

        rdmaChannel.rdmaSendInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf) {
                System.out.println("SEND Success!!!");
            }

            @Override
            public void onFailure(Throwable exception) {

            }
        },new long[]{rdmaBuffer.getAddress()},new int[]{rdmaBuffer.getLkey()},new int[]{rdmaBuffer.getLength()});

        Thread.sleep(Integer.MAX_VALUE);
    }
}
