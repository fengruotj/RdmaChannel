package com.basic.rdmachannel.read;


import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import com.basic.rdmachannel.token.RegionToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.read.RdmaReadServer
 */
public class RdmaReadServer implements RdmaConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(RdmaReadServer.class);
    private static CyclicBarrier cyclicBarrier=new CyclicBarrier(2);
    private static RdmaChannel clientChannel;

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaServer=new RdmaNode("10.10.0.25",1955, new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RDMA_READ_REQUESTOR);
        rdmaServer.bindConnectCompleteListener(new RdmaReadServer());

        cyclicBarrier.await();
        cyclicBarrier.reset();
        RdmaBufferManager rdmaBufferManager = rdmaServer.getRdmaBufferManager();
        RegionToken remoteRegionToken = rdmaServer.getRemoteRegionToken(clientChannel);

        int sizeInBytes=remoteRegionToken.getSizeInBytes();
        long remoteAddress=remoteRegionToken.getAddress();
        int rkey=remoteRegionToken.getLocalKey();//remote的LocalKey

        RdmaBuffer readData = rdmaBufferManager.get(sizeInBytes);
        ByteBuffer readBuffer = readData.getByteBuffer();
        clientChannel.rdmaReadInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer IMM) {
                logger.info("RdmaReadServer receive msg from client: "+readBuffer.asCharBuffer().toString());
            }

            @Override
            public void onFailure(Throwable exception) {
                exception.printStackTrace();
            }
        },readData.getAddress(),readData.getLkey(),new int[]{sizeInBytes},new long[]{remoteAddress},new int[]{rkey});
        Thread.sleep(Integer.MAX_VALUE);
    }

    @Override
    public void onSuccess(InetSocketAddress inetSocketAddress, RdmaChannel rdmaChannel) {
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
