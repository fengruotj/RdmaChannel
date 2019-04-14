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
import com.ibm.disni.rdma.RdmaServerEndpoint;
import com.ibm.disni.rdma.verbs.*;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * DISNI Example ReadServer 服务器程序
 * java -cp disni-1.6-jar-with-dependencies.jar:disni-1.6-tests.jar com.ibm.disni.examples.ReadServer -a 10.10.0.25
 * 2.为自定义的CustomClientEndpoint 实现工厂类RdmaEndpointFactory
 */
public class ReadServer implements RdmaEndpointFactory<ReadServer.CustomServerEndpoint> {
	private RdmaActiveEndpointGroup<ReadServer.CustomServerEndpoint> endpointGroup;
	private String host;
	private int port;
	private int size;
	private int loop;

	public ReadServer(String host, int port, int size, int loop) throws IOException{
		//create a EndpointGroup. The RdmaActiveEndpointGroup contains CQ processing and delivers CQ event to the endpoint.dispatchCqEvent() method.
		endpointGroup = new RdmaActiveEndpointGroup<CustomServerEndpoint>(1000, false, 128, 4, 128);
		endpointGroup.init(this);
		this.host = host;
		this.port = port;
		this.size = size;
		this.loop = loop;
	}

	public ReadServer.CustomServerEndpoint createEndpoint(RdmaCmId idPriv, boolean serverSide) throws IOException {
		return new ReadServer.CustomServerEndpoint(endpointGroup, idPriv, serverSide, size);
	}

	//3.在服务器上，分配EndPoint Group并使用工厂Factory初始化它，创建服务器端点，绑定它并接受连接
	public void run() throws Exception {
		//create a server endpoint
		RdmaServerEndpoint<ReadServer.CustomServerEndpoint> serverEndpoint = endpointGroup.createServerEndpoint();

		//we can call bind on a server endpoint, just like we do with sockets
		InetAddress ipAddress = InetAddress.getByName(host);
		InetSocketAddress address = new InetSocketAddress(ipAddress, port);		
		serverEndpoint.bind(address, 10);
		System.out.println("ReadServer::server bound to address" + address.toString());

		//we can accept new connections
		ReadServer.CustomServerEndpoint endpoint = serverEndpoint.accept();
		System.out.println("ReadServer::connection accepted ");

		//let's prepare a message to be sent to the client
		//in the message we include the RDMA information of a local buffer which we allow the client to read using a one-sided RDMA operation
		ByteBuffer dataBuf = endpoint.getDataBuf();
		ByteBuffer sendBuf = endpoint.getSendBuf();
		IbvMr dataMr = endpoint.getDataMr();
		dataBuf.asCharBuffer().put("This is a RDMA/read on stag " + dataMr.getLkey() + " !");
		dataBuf.clear();
		sendBuf.putLong(dataMr.getAddr());
		sendBuf.putInt(dataMr.getLength());
		sendBuf.putInt(dataMr.getLkey());
		sendBuf.clear();

		//post the operation to send the message
		System.out.println("ReadServer::sending message");
		endpoint.sendMessage();
		//we have to wait for the CQ event, only then we know the message has been sent out
		endpoint.takeEvent();

		//let's wait for the final message to be received. We don't need to check the message itself, just the CQ event is enough.
		endpoint.takeEvent();
		System.out.println("ReadServer::final message");

		//close everything
		endpoint.close();
		serverEndpoint.close();
		endpointGroup.close();
	}

	public static void main(String[] args) throws Exception {
		RdmaBenchmarkCmdLine cmdLine = new RdmaBenchmarkCmdLine("ReadServer");
		try {
			cmdLine.parse(args);
		} catch (ParseException e) {
			cmdLine.printHelp();
			System.exit(-1);
		}

		ReadServer server = new ReadServer(cmdLine.getIp(), cmdLine.getPort(), cmdLine.getSize(), cmdLine.getLoop());
		server.run();
	}

	/**
	 * 1.通过继承RdmaActiveEndpoint来自定义您自己的Endpoint
	 */
	public static class CustomServerEndpoint extends RdmaActiveEndpoint {
		private ByteBuffer buffers[];
		private IbvMr mrlist[];
		private int buffercount = 3;
		private int buffersize;

		private ByteBuffer dataBuf;
		private IbvMr dataMr;
		private ByteBuffer sendBuf;
		private IbvMr sendMr;
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

		public CustomServerEndpoint(RdmaActiveEndpointGroup<CustomServerEndpoint> endpointGroup, RdmaCmId idPriv, boolean serverSide, int size) throws IOException {
			super(endpointGroup, idPriv, serverSide);
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
			this.sendMr = mrlist[1];
			this.recvBuf = buffers[2];
			this.recvMr = mrlist[2];

			sgeSend.setAddr(sendMr.getAddr());
			sgeSend.setLength(sendMr.getLength());
			sgeSend.setLkey(sendMr.getLkey());
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

			this.postRecv(wrList_recv).execute();
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

		public IbvMr getDataMr() {
			return dataMr;
		}

		public IbvMr getSendMr() {
			return sendMr;
		}

		public IbvMr getRecvMr() {
			return recvMr;
		}

		/**
		 * 发送消息
		 * @throws IOException
		 */
		public void sendMessage() throws IOException {
			this.postSend(wrList_send).execute().free();
		}

		/**
		 * 从Complete Queue从取出时间(阻塞方法)
		 * @return
		 * @throws InterruptedException
		 */
		public IbvWC takeEvent() throws InterruptedException{
			return wcEvents.take();
		}
	}

}

