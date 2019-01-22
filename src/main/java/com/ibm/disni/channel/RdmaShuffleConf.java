package com.ibm.disni.channel;

import com.ibm.disni.rdma.verbs.IbvContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * locate org.apache.storm.messaging.rdma
 * Created by mastertj on 2018/8/24.
 */
public class RdmaShuffleConf {
    private static final Logger logger = LoggerFactory.getLogger(RdmaShuffleConf.class);

    private boolean swFlowControl=true;

    private int recvQueueDepth=1024;

    private int sendQueueDepth=4096;

    private int recvWrSize= 4096;

    private int sendWrSize = 4096;

    private int dataWrSize = 4096*2;

    private int rdmaCmEventTimeout= 20000;

    private int teardownListenTimeout= 50;

    private int resolvePathTimeout= 2000;

    private long maxBufferAllocationSize=10*1024*1024;

    private long maxAggPrealloc=0;

    private long maxAggBlock=2*1024;

    private int maxConnectionAttempts=5;

    private int portMaxRetries=16;

    private int port=1955;

    private String serverHost="10.10.0.25";

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

    public int recvQueueDepth() {
        return recvQueueDepth;
    }

    public int sendQueueDepth() {
        return sendQueueDepth;
    }

    public int recvWrSize() {
        return recvWrSize;
    }

    public int sendWrSize() {
        return sendWrSize;
    }

    public int dataWrSize() {
        return dataWrSize;
    }

    public int rdmaCmEventTimeout() {
        return rdmaCmEventTimeout;
    }

    public int teardownListenTimeout() {
        return teardownListenTimeout;
    }

    public int resolvePathTimeout() {
        return resolvePathTimeout;
    }

    public long maxBufferAllocationSize() {
        return maxBufferAllocationSize;
    }

    public long maxAggPrealloc() {
        return maxAggPrealloc;
    }

    public long maxAggBlock() {
        return maxAggBlock;
    }

    public int maxConnectionAttempts() {
        return maxConnectionAttempts;
    }

    public int portMaxRetries() {
        return portMaxRetries;
    }

    public String serverHost() {
        return serverHost;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
