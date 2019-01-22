//package com.ibm.disni.channel;
//
//import com.ibm.disni.rdma.verbs.IbvSendWR;
//import com.ibm.disni.rdma.verbs.IbvSge;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.net.InetSocketAddress;
//import java.nio.ByteBuffer;
//import java.util.LinkedList;
//
///**
// * locate org.apache.storm.messaging.rdma
// * Created by mastertj on 2018/8/27.
// * java -cp rdmaChannel-1.0-SNAPSHOT-jar-with-dependencies.jar:rdmaChannel-1.0-SNAPSHOT-tests.jar com.ibm.disni.channel.RdmaReadClient
// */
//public class RdmaReadClient {
//
//    private static final Logger logger = LoggerFactory.getLogger(RdmaReadClient.class);
//
//    public static void main(String[] args) throws Exception {
//
//        RdmaNode rdmaClient=new RdmaNode("10.10.0.24", true, new RdmaShuffleConf(), new RdmaCompletionListener() {
//            @Override
//            public void onSuccess(ByteBuffer buf) {
//                logger.info("success1111");
//            }
//
//            @Override
//            public void onFailure(Throwable exception) {
//                exception.printStackTrace();
//            }
//        }, (remote, rdmaChannel) -> {
//
//        });
//
//        try {
//            RdmaChannel rdmaChannel = rdmaClient.getRdmaChannel(new InetSocketAddress("10.10.0.25", 1955), true, RdmaChannel.RdmaChannelType.RDMA_READ_REQUESTOR);
//
//            VerbsTools commRdma = rdmaChannel.getCommRdma();
//
//            com.ibm.disni.mr.RdmaBuffer recvMr = rdmaChannel.getReceiveBuffer();
//            ByteBuffer recvBuf = recvMr.getByteBuffer();
//            com.ibm.disni.mr.RdmaBuffer dataMr = rdmaChannel.getDataBuffer();
//            ByteBuffer dataBuf = dataMr.getByteBuffer();
//            com.ibm.disni.mr.RdmaBuffer sendMr = rdmaChannel.getSendBuffer();
//            ByteBuffer sendBuf = sendMr.getByteBuffer();
//
//            logger.info("first add: "+recvBuf.getLong()+" lkey: "+recvBuf.getInt()+" length: "+recvBuf.getInt());
//
//            for(int i = 0;i < 2; i++) {
//
//                //initSGRecv
//                rdmaChannel.initRecvs();
//
//                //let's wait for the first message to be received from the server
//                rdmaChannel.completeSGRecv();
//
//                recvBuf.clear();
//                recvBuf.clear();
//                dataBuf.clear();
//                long addr = recvBuf.getLong();
//                int lkey = recvBuf.getInt();
//                int length = recvBuf.getInt();
//                logger.info("second add: " + addr + " lkey: " + lkey + " length: " + length);
//
//                recvBuf.clear();
//                dataBuf.clear();
//                sendBuf.clear();
//                rdmaChannel.rdmaReadInQueue(new RdmaCompletionListener() {
//                    @Override
//                    public void onSuccess(ByteBuffer buf) {
//                        logger.info("RdmaActiveReadClient::read memory from server: " + buf.asCharBuffer().toString());
//                    }
//
//                    @Override
//                    public void onFailure(Throwable exception) {
//                        exception.printStackTrace();
//                    }
//                }, dataMr.getAddress(), dataMr.getLkey(), new int[]{length}, new long[]{addr}, new int[]{lkey});
//
//                //rdmaChannel.completeSGRecv();
//
//                //let's prepare a one-sided RDMA read operation to fetch the content of that remote buffer
//                LinkedList<IbvSendWR> wrList_send = new LinkedList<IbvSendWR>();
//                IbvSge sgeSend = new IbvSge();
//
//                dataBuf.clear();
//                logger.info(dataBuf.toString());
//                logger.info("RdmaActiveReadClient::read memory from server: " + dataBuf.asCharBuffer().toString());
//
//                LinkedList<IbvSge> sgeList = new LinkedList<IbvSge>();
//                sgeList.add(sgeSend);
//                IbvSendWR sendWR = new IbvSendWR();
//                sgeSend = new IbvSge();
//                sgeSend.setAddr(sendMr.getAddress());
//                sgeSend.setLength(sendMr.getLength());
//                sgeSend.setLkey(sendMr.getLkey());
//                sgeList.clear();
//                sgeList.add(sgeSend);
//                sendWR = new IbvSendWR();
//                sendWR.setWr_id(1002);
//                sendWR.setSg_list(sgeList);
//                sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
//                sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
//                wrList_send.clear();
//                wrList_send.add(sendWR);
//
//                //let's post the final message
//                recvBuf.clear();
//                dataBuf.clear();
//                sendBuf.clear();
//                commRdma.send(wrList_send, true, false);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
