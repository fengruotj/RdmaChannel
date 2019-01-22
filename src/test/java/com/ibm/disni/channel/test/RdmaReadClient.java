package com.ibm.disni.channel.test;

import com.ibm.disni.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * locate org.apache.storm.messaging.rdma
 * Created by mastertj on 2018/8/27.
 * java -cp rdmaChannel-1.0-SNAPSHOT-jar-with-dependencies.jar:rdmaChannel-1.0-SNAPSHOT-tests.jar com.ibm.disni.channel.test.RdmaReadClient
 */
public class RdmaReadClient {

    private static final Logger logger = LoggerFactory.getLogger(RdmaReadClient.class);

    public static void main(String[] args) throws Exception {

        RdmaNode rdmaClient=new RdmaNode("10.10.0.24", true, new RdmaShuffleConf(), new RdmaCompletionListener() {
            @Override
            public void onSuccess(ByteBuffer buf) {
                logger.info("success1111");
            }

            @Override
            public void onFailure(Throwable exception) {
                exception.printStackTrace();
            }
        }, (remote, rdmaChannel) -> {

        });

        RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress("10.10.0.25", 1955), true);

        InetSocketAddress address = null;

        VerbsTools commRdma = rdmaChannel.getCommRdma();

        RdmaBuffer sendMr = rdmaChannel.getSendBuffer();
        ByteBuffer sendBuf = sendMr.getByteBuffer();

        RdmaBuffer recvMr = rdmaChannel.getReceiveBuffer();
        ByteBuffer recvBuf = recvMr.getByteBuffer();

        //dataBuf.asCharBuffer().put("This is a RDMA/read on stag !");

        while (true){
            //initSGRecv
            rdmaChannel.initRecvs();

            ByteBuffer byteBuffer= ByteBuffer.allocateDirect(262184);

            byteBuffer.asCharBuffer().put("This is a RDMA/read on stag !");
            byteBuffer.clear();
            rdmaChannel.setDataBuffer(byteBuffer);

            RdmaBuffer dataMr = rdmaChannel.getDataBuffer();
            ByteBuffer dataBuf = dataMr.getByteBuffer();

            sendBuf.clear();
            sendBuf.putLong(dataMr.getAddress());
            sendBuf.putInt(dataMr.getLkey());
            sendBuf.putInt(dataMr.getLength());
            sendBuf.clear();

            logger.info("first add: " + dataMr.getAddress() + " lkey: " + dataMr.getLkey() + " length: " + dataMr.getLength());
            logger.info("dataBuf: " + dataBuf.asCharBuffer().toString());
            logger.info("sendBuf: " + sendBuf.getLong()+" "+sendBuf.getInt()+" "+sendBuf.getInt());

            //post a send call, here we send a message which include the RDMA information of a data buffer
            recvBuf.clear();
            dataBuf.clear();
            sendBuf.clear();
            rdmaChannel.rdmaSendInQueue(new RdmaCompletionListener() {
                @Override
                public void onSuccess(ByteBuffer buf) {
                    logger.info("RDMA SEND Address Success");
                }

                @Override
                public void onFailure(Throwable exception) {
                    exception.printStackTrace();
                }
            }, new long[]{sendMr.getAddress()}, new int[]{sendMr.getLkey()}, new int[]{sendMr.getLength()});

            //rdmaChannel.completeSGRecv();

            logger.info("RDMA SEND Address Success");

            System.out.println("VerbsServer::stag info sent");

            //wait for the final message from the server
            rdmaChannel.completeSGRecv();

            System.out.println("VerbsServer::done");
        }
    }
}
