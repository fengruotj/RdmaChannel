package com.basic.rdmachannel.sequence;

import com.basic.rdmachannel.channel.RdmaChannel;
import com.basic.rdmachannel.channel.RdmaChannelConf;
import com.basic.rdmachannel.channel.RdmaCompletionListener;
import com.basic.rdmachannel.channel.RdmaNode;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import com.basic.rdmachannel.util.CmdLineCommon;
import com.basic.rdmachannel.util.RDMAUtils;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * locate com.basic.rdmachannel.sequence
 * Created by MasterTj on 2019/8/26.
 * java -cp rdmachannel-multicast-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.sequence.SequenceMulticastClient -a -p -s -a -p -s -i -k -m
 */
public class SequenceMulticastClient{
    private static final Logger logger = LoggerFactory.getLogger(SequenceMulticastClient.class);
    private RdmaNode rdmaClient;
    private RdmaBufferManager rdmaBufferManager;
    private CountDownLatch loopCountDownLatch;
    private CmdLineCommon cmdLine;
    public void run() throws Exception {
        String hostName = RDMAUtils.getLocalHostLANAddress(cmdLine.getIface()).getHostName();
        this.rdmaClient=new RdmaNode(hostName, cmdLine.getPort(), new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RPC);
        this.rdmaBufferManager= rdmaClient.getRdmaBufferManager();
        this.loopCountDownLatch=new CountDownLatch(cmdLine.getLoop());

        RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress(cmdLine.getIp(), cmdLine.getPort()), true, RdmaChannel.RdmaChannelType.RPC);

        // data index transferSize
        RdmaBuffer dataBuffer = rdmaBufferManager.get(cmdLine.getSize());
        ByteBuffer dataByteBuffer = dataBuffer.getByteBuffer();

        int opCount = 0;
        while (opCount < cmdLine.getLoop()) {
            rdmaChannel.rdmaReceiveInQueue(new RdmaCompletionListener() {
                @Override
                public void onSuccess(ByteBuffer buf, Integer IMM) {
                    logger.info("success excute receive request!");
                    loopCountDownLatch.countDown();
                    //logger.info("RdmaWriteServer receive msg from client: "+dataByteBuffer.asCharBuffer().toString());
                }

                @Override
                public void onFailure(Throwable exception) {
                    exception.printStackTrace();
                    loopCountDownLatch.countDown();
                }
            }, dataBuffer.getAddress(), dataBuffer.getLength(), dataBuffer.getLkey());
            opCount++;
        }

        loopCountDownLatch.await();
        //close everything
        rdmaChannel.stop();
        rdmaClient.stop();
    }

    public void launch(String[] args) throws Exception {
        this.cmdLine = new CmdLineCommon("SequenceMulticastClient");

        try {
            cmdLine.parse(args);
        } catch (ParseException e) {
            cmdLine.printHelp();
            System.exit(-1);
        }
        this.run();
    }

    public static void main(String[] args) throws Exception {
        SequenceMulticastClient simpleServer = new SequenceMulticastClient();
        simpleServer.launch(args);
    }

}
