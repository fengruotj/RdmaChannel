package com.basic.rdmachannel.read;

import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.read.RdmaReadClient
 */
public class RdmaReadClient {
    private static final Logger logger = LoggerFactory.getLogger(RdmaReadClient.class);

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaClient=new RdmaNode("10.10.0.24", true, new RdmaChannelConf(), RdmaChannel.RdmaChannelType.RDMA_READ_RESPONDER, new RdmaConnectListener() {
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
        RdmaBuffer rdmaData = rdmaBufferManager.get(4096);
        ByteBuffer dataBuffer = rdmaData.getByteBuffer();
        String str="Hello! I am Client!";
        dataBuffer.asCharBuffer().put(str);
        dataBuffer.flip();

        RdmaBuffer rdmaSend = rdmaBufferManager.get(1024);
        ByteBuffer sendBuffer = rdmaSend.getByteBuffer();
        sendBuffer.putLong(rdmaData.getAddress());
        sendBuffer.putInt(rdmaData.getLkey());
        sendBuffer.putInt(rdmaData.getLength());

        rdmaChannel.rdmaSendInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer IMM) {
                System.out.println("SEND Success!!!");
            }

            @Override
            public void onFailure(Throwable exception) {

            }
        },new long[]{rdmaSend.getAddress()},new int[]{rdmaSend.getLength()},new int[]{rdmaSend.getLkey()});

        Thread.sleep(Integer.MAX_VALUE);
    }
}
