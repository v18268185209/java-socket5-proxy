package com.zqzqq.proxyhub.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProxyPropertiesTest {

    @Test
    void performanceRangesAreValidByDefault() {
        ProxyProperties.PerformanceProperties properties = new ProxyProperties.PerformanceProperties();

        assertTrue(properties.isRecvAllocatorRangeValid());
        assertTrue(properties.isWriteBufferRangeValid());
    }

    @Test
    void performanceRangesRejectInvalidValues() {
        ProxyProperties.PerformanceProperties properties = new ProxyProperties.PerformanceProperties();
        properties.setRecvByteBufMin(8192);
        properties.setRecvByteBufInitial(4096);
        properties.setRecvByteBufMax(16384);
        properties.setWriteBufferLowWaterMark(65536);
        properties.setWriteBufferHighWaterMark(32768);

        assertFalse(properties.isRecvAllocatorRangeValid());
        assertFalse(properties.isWriteBufferRangeValid());
    }
}
