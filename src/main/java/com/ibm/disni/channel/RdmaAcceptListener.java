package com.ibm.disni.channel;

/**
 * locate com.ibm.disni.channel
 * Created by mastertj on 2018/9/12.
 */
public interface RdmaAcceptListener {
    public void OnSuccess(String remote, RdmaChannel rdmaChannel);
}
