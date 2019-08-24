package com.basic.rdmachannel.write;

import com.basic.rdmachannel.channel.RdmaChannel;
import com.basic.rdmachannel.channel.RdmaChannelConf;
import com.basic.rdmachannel.channel.RdmaCompletionListener;
import com.basic.rdmachannel.channel.RdmaNode;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import com.basic.rdmachannel.token.RegionToken;
import com.basic.rdmachannel.util.RDMAUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CyclicBarrier;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * node24
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.write.RdmaWriteClient
 */
public class RdmaWriteClient {
    private static final Logger logger = LoggerFactory.getLogger(RdmaWriteClient.class);
    private static CyclicBarrier cyclicBarrier=new CyclicBarrier(2);

    public static void main(String[] args) throws Exception {
        String hostName = RDMAUtils.getLocalHostLANAddress("ib0").getHostName();
        RdmaNode rdmaClient=new RdmaNode(hostName, 1955,new RdmaChannelConf(), RdmaChannel.RdmaChannelType.RDMA_WRITE_REQUESTOR);

        RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress("10.10.0.25", 1955), true, RdmaChannel.RdmaChannelType.RDMA_READ_RESPONDER);

        RdmaBufferManager rdmaBufferManager = rdmaClient.getRdmaBufferManager();

        RdmaBuffer rdmaData = rdmaBufferManager.get(4096);
        ByteBuffer dataBuffer = rdmaData.getByteBuffer();
        String str="Hello! I am Server!";
        dataBuffer.asCharBuffer().put(str);
        dataBuffer.flip();

        RegionToken remoteRegionToken = rdmaClient.getRemoteRegionToken(rdmaChannel);

        long remoteAddress = remoteRegionToken.getAddress();
        int rkey = remoteRegionToken.getLocalKey();//remote的LocalKey
        int sizeInBytes = remoteRegionToken.getSizeInBytes();

        rdmaChannel.rdmaWriteInQueue(new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf, Integer IMM) {
                logger.info("success excute write request!");
                rdmaBufferManager.put(rdmaData);
            }

            @Override
            public void onFailure(Throwable exception) {
                rdmaBufferManager.put(rdmaData);
            }
        },rdmaData.getAddress(),sizeInBytes,rdmaData.getLkey(),remoteAddress,rkey);

        Thread.sleep(Integer.MAX_VALUE);
    }
}
