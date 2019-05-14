package com.basic.rdmachannel.channel;

import com.ibm.disni.rdma.verbs.IbvContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * locate org.apache.storm.messaging.rdma
 * Created by mastertj on 2018/8/24.
 */
public class RdmaChannelConf {
    private static final Logger logger = LoggerFactory.getLogger(RdmaChannelConf.class);
    private static Properties properties=new Properties();

    private boolean swFlowControl=false;

    private int recvQueueDepth=4096;

    private int sendQueueDepth=4096;

    private int rdmaCmEventTimeout= 20000;

    private int teardownListenTimeout= 50;

    private int resolvePathTimeout= 2000;

    private long maxBufferAllocationSize=10*1024*1024;

    private long maxAggPrealloc=0;

    private long maxAggBlock=2*1024;

    private int maxConnectionAttempts=5;

    private int portMaxRetries=16;

    private String cpuList ="";

    public boolean useOdp(IbvContext context) {
        int rcOdpCaps = 0;
        try {
            rcOdpCaps = context.queryOdpSupport();
        } catch (IOException e) {
            e.printStackTrace();
        }
        boolean ret = (rcOdpCaps != -1) &&
                ((rcOdpCaps & IbvContext.IBV_ODP_SUPPORT_WRITE) != 0) &&
                ((rcOdpCaps & IbvContext.IBV_ODP_SUPPORT_READ) != 0);
        if (!ret) {
            logger.warn("\"ODP (On Demand Paging) is not supported for this device. \" +\n" +
                    "Please refer to the SparkRDMA wiki for more information: \" +\n" +
                    "https://github.com/Mellanox/SparkRDMA/wiki/Configuration-Properties\")");
        } else
            logger.info("Using ODP (On Demand Paging) memory prefetch");

        return ret;
    }

    public boolean swFlowControl() {
        return swFlowControl;
    }

    public void startSwFlowControl(boolean swFlowControl){
        this.swFlowControl=swFlowControl;
    }

    public int recvQueueDepth() {
        return recvQueueDepth;
    }

    public void setRecvQueueDepth(int recvQueueDepth){
        this.recvQueueDepth=recvQueueDepth;
    }

    public int sendQueueDepth() {
        return sendQueueDepth;
    }

    public void setSendQueueDepth(int sendQueueDepth) {
        this.sendQueueDepth=sendQueueDepth;
    }

    public int rdmaCmEventTimeout() {
        return rdmaCmEventTimeout;
    }

    public void setRdmaCmEventTimeout(int rdmaCmEventTimeout) {
        this.rdmaCmEventTimeout=rdmaCmEventTimeout;
    }

    public int teardownListenTimeout() {
        return teardownListenTimeout;
    }

    public void setTeardownListenTimeout(int teardownListenTimeout) {
        this.teardownListenTimeout=teardownListenTimeout;
    }

    public int resolvePathTimeout() {
        return resolvePathTimeout;
    }

    public void setResolvePathTimeout(int resolvePathTimeout) {
        this.resolvePathTimeout=resolvePathTimeout;
    }

    public long maxBufferAllocationSize() {
        return maxBufferAllocationSize;
    }

    public void setMaxBufferAllocationSize(int maxBufferAllocationSize) {
        this.maxBufferAllocationSize=maxBufferAllocationSize;
    }

    public long maxAggPrealloc() {
        return maxAggPrealloc;
    }

    public void setMaxAggPrealloc(int maxAggPrealloc) {
        this.maxAggPrealloc=maxAggPrealloc;
    }

    public long maxAggBlock() {
        return maxAggBlock;
    }

    public void setMaxAggBlock(int maxAggBlock) {
        this.maxAggBlock=maxAggBlock;
    }

    public int maxConnectionAttempts() {
        return maxConnectionAttempts;
    }

    public void setMaxConnectionAttempts(int maxConnectionAttempts) {
        this.maxConnectionAttempts=maxConnectionAttempts;
    }

    public int portMaxRetries() {
        return portMaxRetries;
    }

    public void setPortMaxRetries(int portMaxRetries) {
        this.portMaxRetries=portMaxRetries;
    }

    public String cpuList(){return cpuList;}

    public  void setCpuList(String cpuList){
        this.cpuList=cpuList;
    }

}
