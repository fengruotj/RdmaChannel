package com.basic.rdmachannel.channel;
import java.nio.ByteBuffer;
import java.util.EventListener;

/**
 * locate org.apache.storm.messaging.rdma
 * Created by mastertj on 2018/8/23.
 */
public interface RdmaCompletionListener extends EventListener {
    void onSuccess(ByteBuffer buf);
    void onFailure(Throwable exception); // Must handle multiple calls
}
