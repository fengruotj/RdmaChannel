package com.basic.rdmachannel.channel;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/24.
 */
public interface RdmaConnectListener {
    void onSuccess(RdmaChannel rdmaChannel);
    void onFailure(Throwable exception); // Must handle multiple calls
}
