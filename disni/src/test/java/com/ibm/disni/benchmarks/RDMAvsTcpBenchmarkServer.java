package com.ibm.disni.benchmarks;

import com.ibm.disni.examples.SendRecvServer;
import com.ibm.disni.rdma.RdmaActiveEndpointGroup;
import com.ibm.disni.rdma.RdmaEndpointFactory;
import com.ibm.disni.rdma.RdmaServerEndpoint;
import com.ibm.disni.rdma.verbs.RdmaCmId;
import com.ibm.disni.rdma.verbs.SVCPostRecv;
import com.ibm.disni.rdma.verbs.SVCPostSend;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * locate com.ibm.disni.benchmarks
 * Created by MasterTj on 2019/1/22.
 */
public class RDMAvsTcpBenchmarkServer implements RdmaEndpointFactory<SendRecvServer.CustomServerEndpoint> {
    RdmaActiveEndpointGroup<SendRecvServer.CustomServerEndpoint> endpointGroup;
    private int bufferSize;
    private int loopCount;
    private RdmaServerEndpoint<SendRecvServer.CustomServerEndpoint> serverEndpoint;
    private ServerSocketChannel serverSocket;

    @Override
    public SendRecvServer.CustomServerEndpoint createEndpoint(RdmaCmId id, boolean serverSide) throws IOException {
        return new SendRecvServer.CustomServerEndpoint(endpointGroup, id, serverSide, bufferSize);
    }


    public void runTCP() throws Exception {

        ByteBuffer sendBuf = ByteBuffer.allocateDirect(bufferSize);
        ByteBuffer recvBuf = ByteBuffer.allocateDirect(bufferSize);
        sendBuf.asCharBuffer().put("PONG").clear();
        SocketChannel socketChannel = serverSocket.accept();
        socketChannel.configureBlocking(true);
        socketChannel.socket().setSendBufferSize(bufferSize);
        socketChannel.socket().setReceiveBufferSize(bufferSize);
        System.out.println("Accepted connection from " + socketChannel.getRemoteAddress());
        for (int i = 0; i < loopCount; i++) {
            int read = 0;
            int written = 0;

            // Recv PING
            recvBuf.clear();
            do {
                read += socketChannel.read(recvBuf);
            } while (read != bufferSize);

            //Send PONG
            sendBuf.clear();
            do {
                written += socketChannel.write(sendBuf);
            } while (written != bufferSize);

        }
        socketChannel.close();
        serverSocket.close();
    }

    public void runRDMA() throws Exception {
        //we can accept new connections
        SendRecvServer.CustomServerEndpoint clientEndpoint = serverEndpoint.accept();
        //we have previously passed our own endpoint factory to the group, therefore new endpoints will be of type CustomServerEndpoint
        System.out.println("RDMAvsTcpBenchmarkServer::client connection accepted");
        //in our custom endpoints we have prepared (memory registration and work request creation) some memory buffers beforehand.
        ByteBuffer sendBuf = clientEndpoint.getSendBuf();

        sendBuf.asCharBuffer().put("PONG");
        ByteBuffer recvBuf = clientEndpoint.getRecvBuf();
        SVCPostSend postSend = clientEndpoint.postSend(clientEndpoint.getWrList_send());
        SVCPostRecv postRecv = clientEndpoint.postRecv(clientEndpoint.getWrList_recv());
        for (int i = 0; i < loopCount + 1; i++){
            // Recv PING
            postRecv.execute();
            clientEndpoint.getWcEvents().take();
            recvBuf.clear();

            //Send PONG
            postSend.execute();
            clientEndpoint.getWcEvents().take();
            sendBuf.clear();
        }
        clientEndpoint.close();
        serverEndpoint.close();
        endpointGroup.close();
    }


    public void launch(String[] args) throws Exception {
        RdmaBenchmarkCmdLine cmdLine = new RdmaBenchmarkCmdLine("RDMAvsTcpBenchmarkServer");

        try {
            cmdLine.parse(args);
        } catch (ParseException e) {
            cmdLine.printHelp();
            System.exit(-1);
        }
        String host = cmdLine.getIp();
        Integer port = cmdLine.getPort();
        System.out.println("Address: " + host + ":" + port);
        InetAddress ipAddress = InetAddress.getByName(host);
        InetSocketAddress rdmaAddress = new InetSocketAddress(ipAddress, port);
        bufferSize = cmdLine.getSize();
        System.out.println("Buffer size: " + bufferSize);
        loopCount = cmdLine.getLoop();

        // Start RDMA Server
        //create a EndpointGroup. The RdmaActiveEndpointGroup contains CQ processing and delivers CQ event to the endpoint.dispatchCqEvent() method.
        endpointGroup = new RdmaActiveEndpointGroup<SendRecvServer.CustomServerEndpoint>(1000, false, 128, 4, 128);
        endpointGroup.init(this);
        //create a server endpoint
        serverEndpoint = endpointGroup.createServerEndpoint();

        serverEndpoint.bind(rdmaAddress, 10);
        System.out.println("RdmaVsTcpBenchmarkServer bound to address " + rdmaAddress.toString());

        // Start TCP Server
        InetSocketAddress tcpAddress = new InetSocketAddress(host, port + 1);
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(tcpAddress);
        serverSocket.socket().setReceiveBufferSize(bufferSize);
        System.out.println("TCP server listening " + tcpAddress);

        this.runRDMA();
        this.runTCP();
        System.exit(0);
    }

    public static void main(String[] args) throws Exception {
        RDMAvsTcpBenchmarkServer pingPongServer = new RDMAvsTcpBenchmarkServer();
        pingPongServer.launch(args);
    }
}
