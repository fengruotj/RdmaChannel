package com.basic.rdmachannel.sendrecv;

import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import com.basic.rdmachannel.util.RDMAUtils;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * locate com.basic.rdmachannel.sendrecv
 * Created by MasterTj on 2019/5/16.
 * java -cp rdmachannel-benchmark-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.sendrecv.RdmaSendClient -a 10.10.0.25 -s 4096 -k 1000000
 */
public class RdmaSendClient {
    private static final Logger logger = LoggerFactory.getLogger(RdmaReceiveServer.class);
    private CountDownLatch countDownLatch=new CountDownLatch(1);
    private RdmaChannel clientChannel;

    private String host;
    private int port;
    private int size;
    private int loop;
    private int recvQueueSize;

    public RdmaSendClient(String host, int port, int size, int loop, int recvQueueSize) {
        this.host = host;
        this.port = port;
        this.size = size;
        this.loop = loop;
        this.recvQueueSize = recvQueueSize;
    }

    public static void main(String[] args) throws Exception {
        SendRecvCmdLine cmdLine = new SendRecvCmdLine("RdmaSendClient");
        try {
            cmdLine.parse(args);
        } catch (ParseException e) {
            cmdLine.printHelp();
            System.exit(-1);
        }

        RdmaSendClient client = new RdmaSendClient(cmdLine.getIp(), cmdLine.getPort(),
                cmdLine.getSize(), cmdLine.getLoop(), cmdLine.getQueueDepth());
        client.run();
    }

    private void run() throws Exception {
        String hostName = RDMAUtils.getLocalHostLANAddress("ib0").getHostName();
        RdmaNode rdmaClient = new RdmaNode(hostName, port, new RdmaChannelConf(), RdmaChannel.RdmaChannelType.RPC);

        RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress(host, port), true, RdmaChannel.RdmaChannelType.RPC);

        RdmaBufferManager rdmaBufferManager = rdmaClient.getRdmaBufferManager();
        RdmaBuffer rdmaBuffer = rdmaBufferManager.get(size);
        ByteBuffer byteBuffer = rdmaBuffer.getByteBuffer();

        int opCount = 0;
        long start = System.nanoTime();
        CountDownLatch countDownLatch=new CountDownLatch(loop);
        while (opCount < loop) {
            rdmaChannel.rdmaSendInQueue(new RdmaCompletionListener() {
                @Override
                public void onSuccess(ByteBuffer buf, Integer IMM) {
                    countDownLatch.countDown();
                }

                @Override
                public void onFailure(Throwable exception) {
                    exception.printStackTrace();
                    countDownLatch.countDown();
                }
            }, new long[]{rdmaBuffer.getAddress()}, new int[]{rdmaBuffer.getLength()}, new int[]{rdmaBuffer.getLkey()});
            opCount++;
        }
        countDownLatch.await();
        long end = System.nanoTime();
        long duration = end - start;
        double _ops = (double) loop;
        double _duration = (double) duration;
        double _seconds = _duration / 1000 / 1000 / 1000;
        double iops = _ops / _seconds;
        System.out.println("iops " + iops);
        System.out.println("Bidirectional average latency: " + duration  / (1e6 * loop) + " ms");

        //close everything
        rdmaChannel.stop();
        rdmaClient.stop();
    }
}
