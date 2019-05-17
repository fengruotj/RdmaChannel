package com.basic.rdmachannel.sendrecv;

import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * locate com.basic.rdmachannel.sendrecv
 * Created by MasterTj on 2019/5/16.
 * java -cp rdmachannel-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.sendrecv.RdmaReceiveServer -a 10.10.0.25 -s 4096 -k 1000000
 */
public class RdmaReceiveServer implements RdmaConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(RdmaReceiveServer.class);
    private CountDownLatch countDownLatch=new CountDownLatch(1);
    private RdmaChannel clientChannel;

    private String host;
    private int port;
    private int size;
    private int loop;
    private int recvQueueSize;

    public RdmaReceiveServer(String host, int port, int size, int loop, int recvQueueSize) {
        this.host = host;
        this.port = port;
        this.size = size;
        this.loop = loop;
        this.recvQueueSize = recvQueueSize;
    }

    public static void main(String[] args) throws Exception {
        SendRecvCmdLine cmdLine = new SendRecvCmdLine("SendRecvServer");
        try {
            cmdLine.parse(args);
        } catch (ParseException e) {
            cmdLine.printHelp();
            System.exit(-1);
        }

        RdmaReceiveServer server = new RdmaReceiveServer(cmdLine.getIp(), cmdLine.getPort(),
                cmdLine.getSize(), cmdLine.getLoop(), cmdLine.getQueueDepth());
        server.run();
    }

    private void run() throws Exception {
        RdmaNode rdmaServer=new RdmaNode(host, port, new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RPC);
        rdmaServer.bindConnectCompleteListener(this);

        countDownLatch.await();
        RdmaBufferManager rdmaBufferManager = rdmaServer.getRdmaBufferManager();
        RdmaBuffer rdmaBuffer = rdmaBufferManager.get(size);
        ByteBuffer byteBuffer = rdmaBuffer.getByteBuffer();
        int opCount = 0;
        CountDownLatch countDownLatch=new CountDownLatch(loop);
        while (opCount < loop){
            clientChannel.rdmaReceiveInQueue(new RdmaCompletionListener() {
                @Override
                public void onSuccess(ByteBuffer buf, Integer IMM) {
                    countDownLatch.countDown();
                    //logger.info("success excute receive request!");
                    //logger.info("RdmaWriteServer receive msg from client: "+byteBuffer.asCharBuffer().toString());
                }

                @Override
                public void onFailure(Throwable exception) {
                    exception.printStackTrace();
                    countDownLatch.countDown();
                }
            },rdmaBuffer.getAddress(),rdmaBuffer.getLength(),rdmaBuffer.getLkey());
            opCount++;
        }

        countDownLatch.await();
        //close everything
        rdmaServer.stop();
    }

    @Override
    public void onSuccess(InetSocketAddress inetSocketAddress, RdmaChannel rdmaChannel) {
        logger.info("success accept RdmaChannel: " +inetSocketAddress.getHostName());
        logger.info(rdmaChannel.toString());
        clientChannel=rdmaChannel;
        countDownLatch.countDown();
    }

    @Override
    public void onFailure(Throwable exception) {
        exception.printStackTrace();
    }
}
