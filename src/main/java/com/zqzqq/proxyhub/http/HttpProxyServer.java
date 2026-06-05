package com.zqzqq.proxyhub.http;

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
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "proxy.http", name = "engine", havingValue = "legacy", matchIfMissing = true)
public class HttpProxyServer implements ProxyServer {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyServer.class);

    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;

    public HttpProxyServer(
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
        return "HTTP:" + properties.getHttp().getPort();
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.get() || !properties.getHttp().isEnabled()) {
            return;
        }

        bossGroup = NettyTuningSupport.newBossGroup(properties, "proxy-http-boss");
        workerGroup = NettyTuningSupport.newWorkerGroup(properties, "proxy-http-worker");

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NettyTuningSupport.serverChannelClass(properties))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(NettyTuningSupport.newFlushConsolidationHandler());
                        ch.pipeline().addLast(new IdleStateHandler(properties.getPerformance().getIdleTimeoutSeconds(), 0, 0));
                        ch.pipeline().addLast(new HttpServerCodec());
                        ch.pipeline().addLast(new HttpProxyFrontendHandler(
                                workerGroup,
                                properties,
                                metricsService,
                                accessControlService,
                                authService));
                    }
                });
        NettyTuningSupport.applyServerOptions(bootstrap, properties);

        InetSocketAddress bind = new InetSocketAddress(properties.getHttp().getBindHost(), properties.getHttp().getPort());
        serverChannel = bootstrap.bind(bind).sync().channel();
        running.set(true);
        metricsService.addEvent("INFO", "HTTP proxy listener started at " + bind);
        log.info("HTTP proxy listener started at {}", bind);
        log.info("HTTP effective settings: engine={}, transport={}, authEnabled={}, aclEnabled={}, pendingRequestMaxBytes={}, writeBuffer={}..{}",
                properties.getHttp().getEngine(),
                NettyTuningSupport.transportName(properties),
                properties.getHttp().getAuth().isEnabled(),
                properties.getAcl().isEnabled(),
                properties.getPerformance().getHttpPendingRequestMaxBytes(),
                properties.getPerformance().getWriteBufferLowWaterMark(),
                properties.getPerformance().getWriteBufferHighWaterMark());
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
        metricsService.addEvent("INFO", "HTTP proxy listener stopped");
        log.info("HTTP proxy listener stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
