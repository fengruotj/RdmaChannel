package com.ibm.disni.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 * java -cp rdmaChannel-1.0-SNAPSHOT-jar-with-dependencies.jar:rdmaChannel-1.0-SNAPSHOT-tests.jar com.ibm.disni.channel.RdmaReceiveServer
 */
public class RdmaReceiveServer {
    private static final Logger logger = LoggerFactory.getLogger(RdmaReceiveServer.class);

    public static void main(String[] args) throws Exception {
        RdmaNode rdmaServer=new RdmaNode("10.10.0.25", false, new RdmaShuffleConf(), new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf) {
                logger.info("success receive SEND request!");
                logger.info("RdmaReceiveServer receive msg from client: "+buf.asCharBuffer().toString());
            }

            @Override
            public void onFailure(Throwable exception) {
                exception.printStackTrace();
            }
        });

        Thread.sleep(Integer.MAX_VALUE);
    }
}
