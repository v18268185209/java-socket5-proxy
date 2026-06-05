package com.zqzqq.proxyhub.tcp;

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
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "proxy.tcp", name = "enabled", havingValue = "true", matchIfMissing = false)
public class TcpProxyServer implements ProxyServer {

    private static final Logger log = LoggerFactory.getLogger(TcpProxyServer.class);

    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;

    public TcpProxyServer(
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
        return "TCP:" + properties.getTcp().getPort();
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.get() || !properties.getTcp().isEnabled()) {
            return;
        }

        bossGroup = NettyTuningSupport.newBossGroup(properties, "proxy-tcp-boss");
        workerGroup = NettyTuningSupport.newWorkerGroup(properties, "proxy-tcp-worker");

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NettyTuningSupport.serverChannelClass(properties))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new IdleStateHandler(properties.getPerformance().getIdleTimeoutSeconds(), 0, 0));
                        ch.pipeline().addLast(new TcpProxyFrontendHandler(
                                workerGroup,
                                properties,
                                metricsService,
                                accessControlService,
                                authService));
                    }
                });
        NettyTuningSupport.applyServerOptions(bootstrap, properties);

        InetSocketAddress bind = new InetSocketAddress(properties.getTcp().getBindHost(), properties.getTcp().getPort());
        serverChannel = bootstrap.bind(bind).sync().channel();
        running.set(true);
        metricsService.addEvent("INFO", "TCP proxy listener started at " + bind);
        log.info("TCP proxy listener started at {}", bind);
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
        metricsService.addEvent("INFO", "TCP proxy listener stopped");
        log.info("TCP proxy listener stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
