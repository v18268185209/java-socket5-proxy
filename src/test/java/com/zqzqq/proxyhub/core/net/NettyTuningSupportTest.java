package com.zqzqq.proxyhub.core.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NettyTuningSupportTest {

    @Test
    void prefersEpollOnLinuxWhenAvailable() {
        assertEquals(
                NettyTuningSupport.TransportType.EPOLL,
                NettyTuningSupport.resolveTransportType(true, "Linux", true));
    }

    @Test
    void fallsBackToNioWhenNativeTransportDisabledOrUnavailable() {
        assertEquals(
                NettyTuningSupport.TransportType.NIO,
                NettyTuningSupport.resolveTransportType(false, "Linux", true));
        assertEquals(
                NettyTuningSupport.TransportType.NIO,
                NettyTuningSupport.resolveTransportType(true, "Windows 11", true));
        assertEquals(
                NettyTuningSupport.TransportType.NIO,
                NettyTuningSupport.resolveTransportType(true, "Linux", false));
    }
}
