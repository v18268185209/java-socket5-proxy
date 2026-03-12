package com.zqzqq.proxyhub.core.net;

import com.zqzqq.proxyhub.config.ProxyProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.Locale;

public final class NettyTuningSupport {

    public enum TransportType {
        NIO,
        EPOLL
    }

    private NettyTuningSupport() {
    }

    public static EventLoopGroup newBossGroup(ProxyProperties properties, String threadPrefix) {
        return switch (resolveTransportType(properties)) {
            case EPOLL -> new EpollEventLoopGroup(
                    properties.getPerformance().getBossThreads(),
                    new DefaultThreadFactory(threadPrefix, true));
            case NIO -> new NioEventLoopGroup(
                    properties.getPerformance().getBossThreads(),
                    new DefaultThreadFactory(threadPrefix, true));
        };
    }

    public static EventLoopGroup newWorkerGroup(ProxyProperties properties, String threadPrefix) {
        int workerThreads = properties.getPerformance().getWorkerThreads();
        return switch (resolveTransportType(properties)) {
            case EPOLL -> new EpollEventLoopGroup(
                    workerThreads > 0 ? workerThreads : 0,
                    new DefaultThreadFactory(threadPrefix, true));
            case NIO -> new NioEventLoopGroup(
                    workerThreads > 0 ? workerThreads : 0,
                    new DefaultThreadFactory(threadPrefix, true));
        };
    }

    public static Class<? extends ServerChannel> serverChannelClass(ProxyProperties properties) {
        return resolveTransportType(properties) == TransportType.EPOLL
                ? EpollServerSocketChannel.class
                : NioServerSocketChannel.class;
    }

    public static Class<? extends Channel> clientChannelClass(ProxyProperties properties) {
        return resolveTransportType(properties) == TransportType.EPOLL
                ? EpollSocketChannel.class
                : NioSocketChannel.class;
    }

    public static String transportName(ProxyProperties properties) {
        return resolveTransportType(properties).name().toLowerCase(Locale.ROOT);
    }

    static TransportType resolveTransportType(ProxyProperties properties) {
        return resolveTransportType(
                properties.getPerformance().isPreferNativeTransport(),
                System.getProperty("os.name", ""),
                Epoll.isAvailable());
    }

    static TransportType resolveTransportType(boolean preferNativeTransport, String osName, boolean epollAvailable) {
        if (!preferNativeTransport) {
            return TransportType.NIO;
        }
        String normalizedOs = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalizedOs.contains("linux") && epollAvailable) {
            return TransportType.EPOLL;
        }
        return TransportType.NIO;
    }

    public static void applyServerOptions(ServerBootstrap bootstrap, ProxyProperties properties) {
        ProxyProperties.PerformanceProperties perf = properties.getPerformance();
        bootstrap.option(ChannelOption.SO_BACKLOG, perf.getBacklog())
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(
                        perf.getRecvByteBufMin(),
                        perf.getRecvByteBufInitial(),
                        perf.getRecvByteBufMax()))
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(
                        perf.getWriteBufferLowWaterMark(),
                        perf.getWriteBufferHighWaterMark()));
    }

    public static void applyClientOptions(Bootstrap bootstrap, ProxyProperties properties) {
        ProxyProperties.PerformanceProperties perf = properties.getPerformance();
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, perf.getConnectTimeoutMillis())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(
                        perf.getRecvByteBufMin(),
                        perf.getRecvByteBufInitial(),
                        perf.getRecvByteBufMax()))
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(
                        perf.getWriteBufferLowWaterMark(),
                        perf.getWriteBufferHighWaterMark()));
    }

    public static FlushConsolidationHandler newFlushConsolidationHandler() {
        return new FlushConsolidationHandler(256, true);
    }
}
