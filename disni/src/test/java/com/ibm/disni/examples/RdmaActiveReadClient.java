/*
 * DiSNI: Direct Storage and Networking Interface
 *
 * Author: Patrick Stuedi <stu@zurich.ibm.com>
 *
 * Copyright (C) 2016, IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.ibm.disni.examples;

import com.ibm.disni.benchmarks.RdmaBenchmarkCmdLine;
import com.ibm.disni.rdma.RdmaActiveEndpoint;
import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.RdmaEndpointFactory;
import com.ibm.disni.rdma.verbs.*;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * DISNI Example RdmaActiveReadClient 客户端程序
 * RdmaActiveEndpointGroup Active模式在大量线程下更加健壮，但有较高的延迟。
 * java -cp disni-1.6-jar-with-dependencies.jar:disni-1.6-tests.jar com.ibm.disni.examples.RdmaActiveReadClient -a 10.10.0.25
 * 2.为自定义的CustomClientEndpoint 实现工厂类RdmaEndpointFactory
 */
public class RdmaActiveReadClient implements RdmaEndpointFactory<RdmaActiveReadClient.CustomClientEndpoint> {
    private RdmaActiveEndpointGroup<RdmaActiveReadClient.CustomClientEndpoint> endpointGroup;
    private String host;
    private int port;
    private int size;
    private int loop;

    public RdmaActiveReadClient(String host, int port, int size, int loop) throws IOException{
        //create a EndpointGroup. The RdmaActiveEndpointGroup contains CQ processing and delivers CQ event to the endpoint.dispatchCqEvent() method.
        endpointGroup = new RdmaActiveEndpointGroup<RdmaActiveReadClient.CustomClientEndpoint>(1000, false, 128, 4, 128);
        endpointGroup.init(this);
        this.host = host;
        this.port = port;
        this.size = size;
        this.loop = loop;
    }

    public RdmaActiveReadClient.CustomClientEndpoint createEndpoint(RdmaCmId idPriv, boolean serverSide) throws IOException {
        return new RdmaActiveReadClient.CustomClientEndpoint(endpointGroup, idPriv, serverSide , size);
    }

    //3.在服务器上，分配EndPoint Group并使用工厂Factory初始化它，创建服务器端点，绑定它并接受连接
    public void run() throws Exception {
        //we have passed our own endpoint factory to the group, therefore new endpoints will be of type CustomClientEndpoint
        //let's create a new client endpoint
        RdmaActiveReadClient.CustomClientEndpoint endpoint = endpointGroup.createEndpoint();

        //connect to the server
        InetAddress ipAddress = InetAddress.getByName(host);
        InetSocketAddress address = new InetSocketAddress(ipAddress, port);
        endpoint.connect(address, 1000);
        InetSocketAddress _addr = (InetSocketAddress) endpoint.getDstAddr();
        System.out.println("RdmaActiveReadClient::client connected, address " + _addr.toString());

        //in our custom endpoints we make sure CQ events get stored in a queue, we now query that queue for new CQ events.
        //in this case a new CQ event means we have received some data, i.e., a message from the server
        endpoint.getWcEvents().take();
        ByteBuffer recvBuf = endpoint.getRecvBuf();
        //the message has been received in this buffer
        //it contains some RDMA information sent by the server
        recvBuf.clear();
        long addr = recvBuf.getLong();
        int length = recvBuf.getInt();
        int lkey = recvBuf.getInt();
        recvBuf.clear();
        System.out.println("RdmaActiveReadClient::receiving rdma information, addr " + addr + ", length " + length + ", key " + lkey);
        System.out.println("RdmaActiveReadClient::preparing read operation...");

        //the RDMA information above identifies a RDMA buffer at the server side
        //let's issue a one-sided RDMA read opeation to fetch the content from that buffer
        IbvSendWR sendWR = endpoint.getSendWR();
        sendWR.setWr_id(1001);
        sendWR.setOpcode(IbvSendWR.IBV_WR_RDMA_READ);
        sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
        sendWR.getRdma().setRemote_addr(addr);
        sendWR.getRdma().setRkey(lkey);

        //post the operation on the endpoint
        SVCPostSend postSend = endpoint.postSend(endpoint.getWrList_send());
        for (int i = 10; i <= 100; ){
            postSend.getWrMod(0).getSgeMod(0).setLength(i);
            postSend.execute();
            //wait until the operation has completed
            endpoint.getWcEvents().take();

            //we should have the content of the remote buffer in our own local buffer now
            ByteBuffer dataBuf = endpoint.getDataBuf();
            dataBuf.clear();
            System.out.println("RdmaActiveReadClient::read memory from server: " + dataBuf.asCharBuffer().toString());
            i += 10;
        }

        //let's prepare a final message to signal everything went fine
        sendWR.setWr_id(1002);
        sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
        sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
        sendWR.getRdma().setRemote_addr(addr);
        sendWR.getRdma().setRkey(lkey);

        //post that operation
        endpoint.postSend(endpoint.getWrList_send()).execute().free();

        //close everything
        System.out.println("closing endpoint");
        endpoint.close();
        System.out.println("closing endpoint, done");
        endpointGroup.close();
    }

    public static void main(String[] args) throws Exception {
        RdmaBenchmarkCmdLine cmdLine = new RdmaBenchmarkCmdLine("RdmaPassiveReadClient");
        try {
            cmdLine.parse(args);
        } catch (ParseException e) {
            cmdLine.printHelp();
            System.exit(-1);
        }
        RdmaActiveReadClient client = new RdmaActiveReadClient(cmdLine.getIp(), cmdLine.getPort(), cmdLine.getSize(), cmdLine.getLoop());
        client.run();
    }
    /**
     * 1.通过继承RdmaActiveEndpoint来自定义您自己的Endpoint
     */
    public static class CustomClientEndpoint extends RdmaActiveEndpoint {
        private ByteBuffer buffers[];
        private IbvMr mrlist[];
        private int buffercount = 3;
        private int buffersize = 100;

        private ByteBuffer dataBuf;
        private IbvMr dataMr;
        private ByteBuffer sendBuf;
        private ByteBuffer recvBuf;
        private IbvMr recvMr;

        private LinkedList<IbvSendWR> wrList_send;
        private IbvSge sgeSend;
        private LinkedList<IbvSge> sgeList;
        private IbvSendWR sendWR;

        private LinkedList<IbvRecvWR> wrList_recv;
        private IbvSge sgeRecv;
        private LinkedList<IbvSge> sgeListRecv;
        private IbvRecvWR recvWR;

        private ArrayBlockingQueue<IbvWC> wcEvents;

        public CustomClientEndpoint(RdmaActiveEndpointGroup<? extends CustomClientEndpoint> endpointGroup, RdmaCmId idPriv, boolean isServerSide ,int size) throws IOException {
            super(endpointGroup, idPriv, isServerSide);
            this.buffercount = 3;
            this.buffersize = size;
            buffers = new ByteBuffer[buffercount];
            this.mrlist = new IbvMr[buffercount];

            for (int i = 0; i < buffercount; i++){
                buffers[i] = ByteBuffer.allocateDirect(buffersize);
            }

            this.wrList_send = new LinkedList<IbvSendWR>();
            this.sgeSend = new IbvSge();
            this.sgeList = new LinkedList<IbvSge>();
            this.sendWR = new IbvSendWR();

            this.wrList_recv = new LinkedList<IbvRecvWR>();
            this.sgeRecv = new IbvSge();
            this.sgeListRecv = new LinkedList<IbvSge>();
            this.recvWR = new IbvRecvWR();

            this.wcEvents = new ArrayBlockingQueue<IbvWC>(10);
        }

        //important: we override the init method to prepare some buffers (memory registration, post recv, etc).
        //This guarantees that at least one recv operation will be posted at the moment this endpoint is connected.
        public void init() throws IOException{
            super.init();

            for (int i = 0; i < buffercount; i++){
                mrlist[i] = registerMemory(buffers[i]).execute().free().getMr();
            }

            this.dataBuf = buffers[0];
            this.dataMr = mrlist[0];
            this.sendBuf = buffers[1];
            this.recvBuf = buffers[2];
            this.recvMr = mrlist[2];

            dataBuf.clear();
            sendBuf.clear();

            sgeSend.setAddr(dataMr.getAddr());
            sgeSend.setLength(dataMr.getLength());
            sgeSend.setLkey(dataMr.getLkey());

            sgeList.add(sgeSend);

            sendWR.setWr_id(2000);
            sendWR.setSg_list(sgeList);
            sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
            sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
            wrList_send.add(sendWR);


            sgeRecv.setAddr(recvMr.getAddr());
            sgeRecv.setLength(recvMr.getLength());
            int lkey = recvMr.getLkey();
            sgeRecv.setLkey(lkey);
            sgeListRecv.add(sgeRecv);
            recvWR.setSg_list(sgeListRecv);
            recvWR.setWr_id(2001);
            wrList_recv.add(recvWR);

            System.out.println("RdmaActiveReadClient::initiated recv");
            this.postRecv(wrList_recv).execute().free();
        }

        public void dispatchCqEvent(IbvWC wc) throws IOException {
            wcEvents.add(wc);
        }

        public ArrayBlockingQueue<IbvWC> getWcEvents() {
            return wcEvents;
        }

        public LinkedList<IbvSendWR> getWrList_send() {
            return wrList_send;
        }

        public LinkedList<IbvRecvWR> getWrList_recv() {
            return wrList_recv;
        }

        public ByteBuffer getDataBuf() {
            return dataBuf;
        }

        public ByteBuffer getSendBuf() {
            return sendBuf;
        }

        public ByteBuffer getRecvBuf() {
            return recvBuf;
        }

        public IbvSendWR getSendWR() {
            return sendWR;
        }

        public IbvRecvWR getRecvWR() {
            return recvWR;
        }
    }

}

