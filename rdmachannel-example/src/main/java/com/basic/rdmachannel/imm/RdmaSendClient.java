package com.basic.rdmachannel.imm;

import com.basic.rdmachannel.channel.RdmaChannel;
import com.basic.rdmachannel.channel.RdmaChannelConf;
import com.basic.rdmachannel.channel.RdmaConnectListener;
import com.basic.rdmachannel.channel.RdmaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmachannel-example-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.imm.RdmaSendClient
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

        rdmaChannel.rdmaSendWithImm(2048);

        Thread.sleep(Integer.MAX_VALUE);
    }
}
