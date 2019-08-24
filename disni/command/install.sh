#!/bin/bash
# Building DiSNI

#1.进入到libdisni目录下
cd libdisni
#2.运行准备工作
 ./autoprepare.sh

#3.configure 配置
./configure --with-jdk=/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home
4. 运行make 和install
make && make install
