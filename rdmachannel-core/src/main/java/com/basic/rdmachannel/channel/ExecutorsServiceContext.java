package com.basic.rdmachannel.channel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * locate com.ibm.disni.channel
 * Created by MasterTj on 2019/1/22.
 */
public class ExecutorsServiceContext {
    private static ExecutorService executorService=null;

    public static ExecutorService getInstance(){
        if(executorService==null){
            synchronized (ExecutorsServiceContext.class){
                if(executorService==null)
                    executorService= Executors.newCachedThreadPool();
            }
        }
        return executorService;
    }
}
