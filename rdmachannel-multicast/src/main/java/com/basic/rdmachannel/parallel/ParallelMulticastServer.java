package com.basic.rdmachannel.parallel;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * locate com.basic.rdmachannel.sequence
 * Created by MasterTj on 2019/8/26.
 * java -cp rdmachannel-multicast-1.0-SNAPSHOT-jar-with-dependencies.jar com.basic.rdmachannel.parallel.ParallelMulticastServer -a -p -s -i -k -m
 */
public class ParallelMulticastServer implements RdmaConnectListener {
    private static final Logger logger = LoggerFactory.getLogger(ParallelMulticastServer.class);
    private CountDownLatch conectionCountDownLatch;
    private CountDownLatch finishTaskCountDownLatch;
    private RdmaNode rdmaServer;
    private RdmaBufferManager rdmaBufferManager;

    private ExecutorService executorService;

    private long start = 0L;
    private long end = 0L;

    private CmdLineCommon cmdLine;
    public void run() throws Exception {
        String hostName = RDMAUtils.getLocalHostLANAddress(cmdLine.getIface()).getHostName();
        this.conectionCountDownLatch = new CountDownLatch(cmdLine.getNum());
        this.finishTaskCountDownLatch = new CountDownLatch(cmdLine.getNum());
        this.executorService = Executors.newCachedThreadPool();

        this.rdmaServer=new RdmaNode(hostName, cmdLine.getPort(), new RdmaChannelConf() , RdmaChannel.RdmaChannelType.RPC);
        this.rdmaServer.bindConnectCompleteListener(this);
        this.rdmaBufferManager= rdmaServer.getRdmaBufferManager();
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

    public void stop() throws Exception {
        //close everything
        rdmaServer.stop();
        executorService.shutdown();
    }

    public void printBench(){
        long duration = end - start;
        double _ops = (double) cmdLine.getLoop();
        double _duration = (double) duration;
        double _seconds = _duration / 1000 / 1000 / 1000;
        double iops = _ops / _seconds;
        System.out.println("iops " + iops);
        System.out.println("Bidirectional average latency: " + duration  / (1e6 * cmdLine.getLoop()) + " ms");
    }

    public static void main(String[] args) throws Exception {
        ParallelMulticastServer simpleServer = new ParallelMulticastServer();
        simpleServer.launch(args);
        simpleServer.getFinishTaskCountDownLatch().await();
        simpleServer.printBench();
        simpleServer.stop();
    }

    @Override
    public void onSuccess(InetSocketAddress inetSocketAddress, RdmaChannel rdmaChannel) {
        logger.info("success accept RdmaChannel: " + inetSocketAddress.getHostName());
        logger.info("channel: " + rdmaChannel.toString());

        executorService.submit(new ParallelMulticastTask(cmdLine, rdmaChannel, rdmaBufferManager));

        conectionCountDownLatch.countDown();
    }

    @Override
    public void onFailure(Throwable exception) {
        exception.printStackTrace();
    }

    private class ParallelMulticastTask implements Runnable{
        private final Logger logger = LoggerFactory.getLogger(ParallelMulticastTask.class);
        private RdmaChannel rdmaChannel;
        private RdmaBufferManager rdmaBufferManager;
        private CmdLineCommon cmdLine;

        public ParallelMulticastTask(CmdLineCommon cmdLine, RdmaChannel rdmaChannel, RdmaBufferManager rdmaBufferManager) {
            this.rdmaChannel = rdmaChannel;
            this.rdmaBufferManager=rdmaBufferManager;
            this.cmdLine = cmdLine;
        }

        @Override
        public void run() {
            try {
                // wait all the rdma connections
                conectionCountDownLatch.await();

                // data index transferSize
                RdmaBuffer dataBuffer = rdmaBufferManager.getDirect(cmdLine.getSize());
                ByteBuffer dataByteBuffer = dataBuffer.getByteBuffer();

                CountDownLatch countDownLatch=new CountDownLatch(cmdLine.getLoop());

                // start benchmark multicast
                int opCount = 0;
                start = System.nanoTime();
                while (opCount < cmdLine.getLoop()) {
                    rdmaChannel.rdmaSendInQueue(new RdmaCompletionListener() {
                        @Override
                        public void onSuccess(ByteBuffer buf, Integer IMM) {
                            try {
                                logger.info("success excute send request!");
                                countDownLatch.countDown();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(Throwable exception) {
                            exception.printStackTrace();
                            try {
                                countDownLatch.countDown();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, new long[]{dataBuffer.getAddress()}, new int[]{dataBuffer.getLength()}, new int[]{dataBuffer.getLkey()});
                    opCount++;
                }
                countDownLatch.await();

                end = System.nanoTime();

                finishTaskCountDownLatch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public CountDownLatch getFinishTaskCountDownLatch() {
        return finishTaskCountDownLatch;
    }
}
