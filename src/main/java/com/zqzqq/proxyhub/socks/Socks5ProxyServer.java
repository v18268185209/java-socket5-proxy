package com.zqzqq.proxyhub.socks;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.ProxyServer;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.net.NettyTuningSupport;
import com.zqzqq.proxyhub.core.security.AuthService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Socks5ProxyServer implements ProxyServer {

    private static final Logger log = LoggerFactory.getLogger(Socks5ProxyServer.class);

    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;

    public Socks5ProxyServer(
            ProxyProperties properties,
            ProxyMetricsService metricsService,
            AccessControlService accessControlService,
            AuthService authService) {
        this.properties = properties;
        this.metricsService = metricsService;
        this.accessControlService = accessControlService;
        this.authService = authService;
    }

    @Override
    public String name() {
        return "SOCKS5:" + properties.getSocks().getPort();
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.get() || !properties.getSocks().isEnabled()) {
            return;
        }

        bossGroup = NettyTuningSupport.newBossGroup(properties, "proxy-socks-boss");
        workerGroup = NettyTuningSupport.newWorkerGroup(properties, "proxy-socks-worker");

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NettyTuningSupport.serverChannelClass(properties))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(NettyTuningSupport.newFlushConsolidationHandler());
                        ch.pipeline().addLast(new IdleStateHandler(properties.getPerformance().getIdleTimeoutSeconds(), 0, 0));
                        ch.pipeline().addLast(new Socks5InitialRequestDecoder());
                        ch.pipeline().addLast(Socks5ServerEncoder.DEFAULT);
                        ch.pipeline().addLast(new Socks5FrontendHandler(
                                workerGroup,
                                properties,
                                metricsService,
                                accessControlService,
                                authService));
                    }
                });
        NettyTuningSupport.applyServerOptions(bootstrap, properties);

        InetSocketAddress bind = new InetSocketAddress(properties.getSocks().getBindHost(), properties.getSocks().getPort());
        serverChannel = bootstrap.bind(bind).sync().channel();
        running.set(true);
        metricsService.addEvent("INFO", "SOCKS5 listener started at " + bind);
        log.info("SOCKS5 listener started at {}", bind);
        log.info("SOCKS5 effective settings: transport={}, authEnabled={}, aclEnabled={}, maxConnectionsPerClient={}",
                NettyTuningSupport.transportName(properties),
                properties.getSocks().getAuth().isEnabled(),
                properties.getAcl().isEnabled(),
                properties.getPerformance().getMaxConnectionsPerClient());
    }

    @Override
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        running.set(false);
        metricsService.addEvent("INFO", "SOCKS5 listener stopped");
        log.info("SOCKS5 listener stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
