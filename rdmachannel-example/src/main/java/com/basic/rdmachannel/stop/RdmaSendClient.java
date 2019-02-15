package com.basic.rdmachannel.stop;

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
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.stop.RdmaSendClient
 */
public class RdmaSendClient {
    private static final Logger logger = LoggerFactory.getLogger(RdmaSendClient.class);

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaClient=new RdmaNode("10.10.0.24", true, new RdmaChannelConf(), RdmaChannel.RdmaChannelType.RPC, new RdmaConnectListener() {
            @Override
            public void onSuccess(RdmaChannel rdmaChannel) {
                logger.info("success connect");
            }

            @Override
            public void onFailure(Throwable exception) {
                exception.printStackTrace();
            }
        });

        RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress("10.10.0.25", 1955), true, RdmaChannel.RdmaChannelType.RPC);

        RdmaBufferManager rdmaBufferManager = rdmaClient.getRdmaBufferManager();
        RdmaBuffer rdmaBuffer = rdmaBufferManager.get(1024);
        ByteBuffer byteBuffer = rdmaBuffer.getByteBuffer();
        String str="Hello! I am Client!";
        byteBuffer.asCharBuffer().put(str);
        byteBuffer.flip();

        rdmaChannel.rdmaSendInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer IMM) {
                System.out.println("SEND Success!!!");
            }

            @Override
            public void onFailure(Throwable exception) {

            }
        },new long[]{rdmaBuffer.getAddress()},new int[]{rdmaBuffer.getLength()},new int[]{rdmaBuffer.getLkey()});

        rdmaChannel.stop();
        rdmaClient.stop();
        Thread.sleep(Integer.MAX_VALUE);
    }
}
