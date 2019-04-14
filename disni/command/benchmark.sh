#!/bin/bash

#简单测试RDMA client-Server模型
#在node25节点 Server端运行
java -cp disni-1.6-jar-with-dependencies.jar:disni-1.6-tests.jar com.ibm.disni.examples.SendRecvServer -a 10.10.0.25

#在node24节点 Client端运行
java -cp disni-1.6-jar-with-dependencies.jar:disni-1.6-tests.jar com.ibm.disni.examples.SendRecvClient -a 10.10.0.25

