package com.basic.rdmachannel;

import com.basic.rdmachannel.channel.RdmaChannel;
import com.basic.rdmachannel.channel.RdmaChannelConf;
import com.basic.rdmachannel.channel.RdmaNode;
import com.basic.rdmachannel.util.RDMAUtils;

/**
 * locate org.apache.storm.rdma
 * Created by MasterTj on 2019/4/11.
 */
public class RDMANodeContainer {
    private volatile static RdmaNode rdmaNode;
    private volatile static int port;

    public static int getPort() {
        return port;
    }

    public static void setPort(int port) {
        RDMANodeContainer.port = port;
    }

    public static void initInstance(int port){
        try {
            String hostName = RDMAUtils.getLocalHostLANAddress("ib0").getHostName();
            RdmaChannelConf rdmaChannelConf = new RdmaChannelConf();
            rdmaChannelConf.setSendQueueDepth(8192);
            rdmaChannelConf.setRecvQueueDepth(8192);
            rdmaChannelConf.setOrderControl(true);
            rdmaNode=new RdmaNode(hostName,port,rdmaChannelConf, RdmaChannel.RdmaChannelType.RPC);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static RdmaNode getInstance(){
        if(rdmaNode==null) {
            synchronized (RDMANodeContainer.class) {
                if(rdmaNode==null){
                    initInstance(port);
                }
            }
        }
        return rdmaNode;
    }
}
