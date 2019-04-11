package com.basic.rdmachannel.imm;


import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.util.RDMAUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.imm.RdmaReceiveServer
 */
public class RdmaReceiveServer implements RdmaConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(RdmaReceiveServer.class);
    private static CountDownLatch countDownLatch=new CountDownLatch(1);
    private static RdmaChannel clientChannel;

    public static void main(String[] args) throws Exception {
        String hostName = RDMAUtils.getLocalHostLANAddress("ib0").getHostName();
        RdmaNode rdmaServer=new RdmaNode(hostName,1955, new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RPC);
        rdmaServer.bindConnectCompleteListener(new RdmaReceiveServer());

        countDownLatch.await();

        clientChannel.rdmaRecvWithImm(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer imm) {
                logger.info("immdata Data : "+ imm);
            }

            @Override
            public void onFailure(Throwable exception) {

            }
        });
        Thread.sleep(Integer.MAX_VALUE);
    }

    @Override
    public void onSuccess(InetSocketAddress inetSocketAddress, RdmaChannel rdmaChannel) {
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
