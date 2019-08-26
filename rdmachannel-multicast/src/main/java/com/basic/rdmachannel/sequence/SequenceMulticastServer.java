package com.basic.rdmachannel.sequence;

import com.basic.rdmachannel.channel.*;
import com.basic.rdmachannel.mr.RdmaBuffer;
import com.basic.rdmachannel.mr.RdmaBufferManager;
import com.basic.rdmachannel.util.CmdLineCommon;
import com.basic.rdmachannel.util.RDMAUtils;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * locate com.basic.rdmachannel.sequence
 * Created by MasterTj on 2019/8/26.
 * java -cp rdmachannel-multicast-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.sequence.SequenceMulticastServer -a -p -s -i -k -m
 */
public class SequenceMulticastServer implements RdmaConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(SequenceMulticastServer.class);
    private CountDownLatch conectionCountDownLatch;
    private RdmaNode rdmaServer;
    private RdmaBufferManager rdmaBufferManager;
    private List<RdmaChannel> channelList=new ArrayList<>();

    private CmdLineCommon cmdLine;
    public void run() throws Exception {
        String hostName = RDMAUtils.getLocalHostLANAddress(cmdLine.getIface()).getHostName();
        this.conectionCountDownLatch=new CountDownLatch(cmdLine.getNum());

        this.rdmaServer=new RdmaNode(hostName, cmdLine.getPort(), new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RPC);
        this.rdmaServer.bindConnectCompleteListener(this);
        this.rdmaBufferManager= rdmaServer.getRdmaBufferManager();

        // data index transferSize
        RdmaBuffer dataBuffer = rdmaBufferManager.get(cmdLine.getSize());
        ByteBuffer dataByteBuffer = dataBuffer.getByteBuffer();

        // wait all the rdma connections
        conectionCountDownLatch.await();

        CyclicBarrier cyclicBarrier = new CyclicBarrier(2);

        // start benchmark multicast
        int opCount = 0;
        long start = System.nanoTime();
        while (opCount < cmdLine.getLoop()) {
            for (int i = 0; i < channelList.size(); i++) {
                cyclicBarrier.reset();
                RdmaChannel rdmaChannel = channelList.get(i);
                rdmaChannel.rdmaSendInQueue(new RdmaCompletionListener() {
                    @Override
                    public void onSuccess(ByteBuffer buf, Integer IMM) {
                        try {
                            logger.info("success excute send request!");
                            cyclicBarrier.await();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onFailure(Throwable exception) {
                        exception.printStackTrace();
                        try {
                            cyclicBarrier.await();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, new long[]{dataBuffer.getAddress()}, new int[]{dataBuffer.getLength()}, new int[]{dataBuffer.getLkey()});
                cyclicBarrier.await();
            }
            opCount++;
        }

        long end = System.nanoTime();
        long duration = end - start;
        double _ops = (double) cmdLine.getLoop();
        double _duration = (double) duration;
        double _seconds = _duration / 1000 / 1000 / 1000;
        double iops = _ops / _seconds;
        System.out.println("iops " + iops);
        System.out.println("Bidirectional average latency: " + duration  / (1e6 * cmdLine.getLoop()) + " ms");

        //close everything
        rdmaServer.stop();
    }

    public void launch(String[] args) throws Exception {
        this.cmdLine = new CmdLineCommon("ParallelMulticastServer");

        try {
            cmdLine.parse(args);
        } catch (ParseException e) {
            cmdLine.printHelp();
            System.exit(-1);
        }
        this.run();
    }

    public static void main(String[] args) throws Exception {
        SequenceMulticastServer simpleServer = new SequenceMulticastServer();
        simpleServer.launch(args);
        //Thread.sleep(Integer.MAX_VALUE);
    }

    @Override
    public void onSuccess(InetSocketAddress inetSocketAddress, RdmaChannel rdmaChannel) {
        logger.info("success accept RdmaChannel: " + inetSocketAddress.getHostName());
        logger.info("channel: " + rdmaChannel.toString());
        channelList.add(rdmaChannel);

        conectionCountDownLatch.countDown();
    }

    @Override
    public void onFailure(Throwable exception) {
        exception.printStackTrace();
    }
}
