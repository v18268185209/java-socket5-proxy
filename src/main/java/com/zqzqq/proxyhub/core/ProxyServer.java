package com.zqzqq.proxyhub.core;

public interface ProxyServer {

    String name();

    void start() throws Exception;

    void stop();

    boolean isRunning();
}
