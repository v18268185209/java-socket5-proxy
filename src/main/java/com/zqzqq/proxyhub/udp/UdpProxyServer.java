package com.zqzqq.proxyhub.udp;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.ProxyServer;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.security.UserStore;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "proxy.udp", name = "enabled", havingValue = "true", matchIfMissing = false)
public class UdpProxyServer implements ProxyServer {

    private static final Logger log = LoggerFactory.getLogger(UdpProxyServer.class);

    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final UserStore userStore;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile EventLoopGroup eventLoopGroup;
    private volatile Channel serverChannel;

    public UdpProxyServer(
            ProxyProperties properties,
            ProxyMetricsService metricsService,
            AccessControlService accessControlService,
            UserStore userStore) {
        this.properties = properties;
        this.metricsService = metricsService;
        this.accessControlService = accessControlService;
        this.userStore = userStore;
    }

    @Override
    public String name() {
        return "UDP:" + properties.getUdp().getPort();
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.get() || !properties.getUdp().isEnabled()) {
            return;
        }

        int workerThreads = properties.getPerformance().getWorkerThreads() > 0
                ? properties.getPerformance().getWorkerThreads() : 2;
        eventLoopGroup = new NioEventLoopGroup(workerThreads,
                new io.netty.util.concurrent.DefaultThreadFactory("proxy-udp"));

        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, false)
                .option(ChannelOption.SO_RCVBUF, 65536)
                .handler(new UdpProxyFrontendHandler(
                        properties,
                        metricsService,
                        accessControlService,
                        userStore));

        InetSocketAddress bind = new InetSocketAddress(properties.getUdp().getBindHost(), properties.getUdp().getPort());
        serverChannel = bootstrap.bind(bind).sync().channel();
        running.set(true);
        metricsService.addEvent("INFO", "UDP proxy listener started at " + bind);
        log.info("UDP proxy listener started at {}", bind);
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
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
            eventLoopGroup = null;
        }
        running.set(false);
        metricsService.addEvent("INFO", "UDP proxy listener stopped");
        log.info("UDP proxy listener stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
