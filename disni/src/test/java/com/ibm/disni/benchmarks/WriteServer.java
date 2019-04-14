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

package com.ibm.disni.benchmarks;

import com.ibm.disni.rdma.*;
import com.ibm.disni.rdma.verbs.*;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * DISNI Benchmark WriteServer 服务器程序
 * java -cp disni-1.6-jar-with-dependencies.jar:disni-1.6-tests.jar com.ibm.disni.benchmarks.WriteServer -a 10.10.0.25 -s 64 -k 1000
 */
public class WriteServer implements RdmaEndpointFactory<WriteServer.WriteServerEndpoint> {
	private RdmaActiveEndpointGroup<WriteServerEndpoint> group;
	private String host;
	private int port;
	private int size;
	private int loop;

	public WriteServer(String host, int port, int size, int loop) throws IOException{
		this.group = new RdmaActiveEndpointGroup<WriteServer.WriteServerEndpoint>(1, false, 128, 4, 128);
		this.group.init(this);
		this.host = host;
		this.port = port;
		this.size = size;
		this.loop = loop;
	}

	public WriteServer.WriteServerEndpoint createEndpoint(RdmaCmId id, boolean serverSide)
			throws IOException {
		return new WriteServerEndpoint(group, id, serverSide, size);
	}


	private void run() throws Exception {
		System.out.println("WriteServer, size " + size + ", loop " + loop);

		RdmaServerEndpoint<WriteServer.WriteServerEndpoint> serverEndpoint = group.createServerEndpoint();
		InetAddress ipAddress = InetAddress.getByName(host);
		InetSocketAddress address = new InetSocketAddress(ipAddress, port);				
		serverEndpoint.bind(address, 10);
		WriteServer.WriteServerEndpoint endpoint = serverEndpoint.accept();
		System.out.println("WriteServer, client connected, address " + address.toString());

		//let's prepare a message to be sent to the client
		//in the message we include the RDMA information of a local buffer which we allow the client to read using a one-sided RDMA operation
		ByteBuffer sendBuf = endpoint.getSendBuf();
		IbvMr dataMr = endpoint.getDataMr();

		sendBuf.putLong(dataMr.getAddr());
		sendBuf.putInt(dataMr.getLength());
		sendBuf.putInt(dataMr.getLkey());
		sendBuf.clear();

		//post the operation to send the message
		System.out.println("WriteServer::sending target address");
		endpoint.sendMessage();
		//we have to wait for the CQ event, only then we know the message has been sent out
		endpoint.takeEvent();

		//let's wait for the final message to be received. We don't need to check the message itself, just the CQ event is enough.
		endpoint.takeEvent();
		System.out.println("WriteServer, final message");

		//we should have the content of the remote buffer in our own local buffer now
//		ByteBuffer dataBuf = endpoint.getDataBuf();
//		dataBuf.clear();
//		System.out.println("WriteServer::write memory from client: " + dataBuf.asCharBuffer().toString()+" "+endpoint.wcEvents.size());

		//close everything
		endpoint.close();
		serverEndpoint.close();
		group.close();
	}


	public static void main(String[] args) throws Exception {
		RdmaBenchmarkCmdLine cmdLine = new RdmaBenchmarkCmdLine("WriteServer");
		try {
			cmdLine.parse(args);
		} catch (ParseException e) {
			cmdLine.printHelp();
			System.exit(-1);
		}

		WriteServer server = new WriteServer(cmdLine.getIp(), cmdLine.getPort(), cmdLine.getSize(), cmdLine.getLoop());
		server.run();
	}

	public static class WriteServerEndpoint extends RdmaActiveEndpoint {
		private ArrayBlockingQueue<IbvWC> wcEvents;

		private ByteBuffer buffers[];
		private IbvMr mrlist[];
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


		protected WriteServerEndpoint(RdmaActiveEndpointGroup<? extends RdmaEndpoint> group, RdmaCmId idPriv, boolean serverSide, int size) throws IOException {
			super(group, idPriv, serverSide);
			this.buffersize = size;
			buffers = new ByteBuffer[3];
			this.mrlist = new IbvMr[3];

			for (int i = 0; i < 3; i++){
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

		public void sendMessage() throws IOException {
			this.postSend(wrList_send).execute().free();
		}

		@Override
		protected synchronized void init() throws IOException {
			super.init();
			for (int i = 0; i < 3; i++){
				mrlist[i] = registerMemory(buffers[i]).execute().free().getMr();
			}

			this.dataBuf = buffers[0];
			this.dataMr = mrlist[0];
			this.sendBuf = buffers[1];
			this.sendMr = mrlist[1];
			this.recvBuf = buffers[2];
			this.recvMr = mrlist[2];

			ByteBuffer sendBuf = buffers[1];
			sendBuf.putLong(dataMr.getAddr());
			sendBuf.putInt(dataMr.getLength());
			sendBuf.putInt(dataMr.getLkey());
			sendBuf.clear();

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

		public IbvWC takeEvent() throws InterruptedException{
			return wcEvents.take();
		}

		public IbvMr getDataMr() {
			return dataMr;
		}

		public void setDataMr(IbvMr dataMr) {
			this.dataMr = dataMr;
		}

		public ByteBuffer getSendBuf() {
			return sendBuf;
		}

		public void setSendBuf(ByteBuffer sendBuf) {
			this.sendBuf = sendBuf;
		}

		public ByteBuffer getDataBuf() {
			return dataBuf;
		}

		public void setDataBuf(ByteBuffer dataBuf) {
			this.dataBuf = dataBuf;
		}
	}
}
