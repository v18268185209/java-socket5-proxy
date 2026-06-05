package com.zqzqq.proxyhub.udp;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.security.UserStore;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * UDP 代理前端处理器
 * 支持：
 * 1. DNS 代理（默认转发到 8.8.8.8:53）
 * 2. 明文认证：AUTH <user>:<pass>\r\n
 * 3. 格式：host:port\r\n<data>
 */
public class UdpProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(UdpProxyFrontendHandler.class);
    private static final int SESSION_TIMEOUT_SECONDS = 120;

    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final UserStore userStore;

    // senderKey -> (sessionId, createTime)
    private final Map<String, UdpSessionInfo> sessions = new ConcurrentHashMap<>();

    public UdpProxyFrontendHandler(
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
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof DatagramPacket packet)) {
            return;
        }

        byte[] data = ByteBufUtil.getBytes(packet.content());
        InetSocketAddress sender = packet.sender();
        String senderKey = sender.getAddress().getHostAddress() + ":" + sender.getPort();
        String text = new String(data, CharsetUtil.UTF_8);

        // 检查客户端 CIDR
        if (!accessControlService.isClientAllowed(senderKey)) {
            log.warn("UDP proxy blocked client: {}", senderKey);
            return;
        }

        // 认证
        if (text.startsWith("AUTH ") && properties.getUdp().getAuth().isEnabled()) {
            handleAuth(ctx, packet, text.substring(5).trim());
            return;
        }

        // 转发
        forwardPacket(ctx, packet, text);
    }

    private void handleAuth(ChannelHandlerContext ctx, DatagramPacket packet, String creds) {
        String[] parts = creds.split(":", 2);
        if (parts.length < 2 || !userStore.validateProxyUser(parts[0], parts[1])) {
            log.warn("UDP proxy auth failed from {}", packet.sender());
            return;
        }
        ctx.writeAndFlush(new DatagramPacket(
                Unpooled.copiedBuffer("200 OK\r\n", CharsetUtil.UTF_8), packet.sender()));
        log.info("UDP proxy authenticated user: {} from {}", parts[0], packet.sender());
    }

    private void forwardPacket(ChannelHandlerContext ctx, DatagramPacket packet, String payload) {
        InetSocketAddress sender = packet.sender();
        String senderKey = sender.getAddress().getHostAddress() + ":" + sender.getPort();

        // 解析目标 host:port\r\n<data>，使用 holder 保证 effectively final
        TargetHolder target = parseTarget(payload);

        // ACL 检查
        if (!accessControlService.isTargetAllowed(target.host, target.port)) {
            log.warn("UDP proxy ACL blocked: {}:{} from {}", target.host, target.port, senderKey);
            metricsService.recordFailure(ProxyFailureReason.ACL_DENIED, "UDP blocked " + target.host + ":" + target.port);
            return;
        }

        // 创建 session
        String sessionId = UUID.randomUUID().toString();
        sessions.put(senderKey, new UdpSessionInfo(sessionId, System.currentTimeMillis()));

        metricsService.addEvent("INFO", "UDP proxy to " + target.host + ":" + target.port);

        // 转发 UDP
        EventLoop eventLoop = ctx.channel().eventLoop();

        io.netty.bootstrap.Bootstrap udpBootstrap = new io.netty.bootstrap.Bootstrap();
        udpBootstrap.group(eventLoop)
                .channel(io.netty.channel.socket.nio.NioDatagramChannel.class)
                .handler(new io.netty.channel.ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext chc) {
                        chc.writeAndFlush(new DatagramPacket(
                                Unpooled.copiedBuffer(target.body),
                                new InetSocketAddress(target.host, target.port)))
                                .addListener((ChannelFutureListener) f -> {
                                    if (!f.isSuccess()) {
                                        metricsService.recordFailure(ProxyFailureReason.UPSTREAM_CONNECT_FAILED,
                                                "UDP send failed to " + target.host + ":" + target.port);
                                    }
                                });
                    }
                });

        try {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<byte[]> resp = new java.util.concurrent.atomic.AtomicReference<>();

            udpBootstrap.handler(new io.netty.channel.ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext chc, Object msg) {
                    if (msg instanceof DatagramPacket dp) {
                        byte[] r = ByteBufUtil.getBytes(dp.content());
                        resp.set(r);
                        latch.countDown();
                        chc.close();
                    }
                }
            });

            udpBootstrap.connect().sync();

            if (latch.await(SESSION_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                byte[] r = resp.get();
                if (r != null) {
                    ctx.writeAndFlush(new DatagramPacket(
                            Unpooled.copiedBuffer(r), sender));
                    metricsService.recordFromTarget(sessionId, r.length);
                }
            }

            sessions.remove(senderKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析 payload: host:port\r\n<data> 或纯 data（默认 DNS）
     */
    private TargetHolder parseTarget(String payload) {
        String targetHost;
        int targetPort;
        byte[] body;

        int crlfIdx = payload.indexOf("\r\n");
        if (crlfIdx > 0) {
            String[] hp = payload.substring(0, crlfIdx).split(":", 2);
            if (hp.length == 2) {
                try {
                    targetHost = hp[0];
                    targetPort = Integer.parseInt(hp[1]);
                } catch (NumberFormatException e) {
                    targetHost = "8.8.8.8";
                    targetPort = 53;
                }
            } else {
                targetHost = "8.8.8.8";
                targetPort = 53;
            }
            body = payload.substring(crlfIdx + 2).getBytes(CharsetUtil.UTF_8);
        } else {
            targetHost = "8.8.8.8";
            targetPort = 53;
            body = payload.getBytes(CharsetUtil.UTF_8);
        }
        return new TargetHolder(targetHost, targetPort, body);
    }

    private static class TargetHolder {
        final String host;
        final int port;
        final byte[] body;
        TargetHolder(String host, int port, byte[] body) {
            this.host = host;
            this.port = port;
            this.body = body;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("UDP proxy exception", cause);
    }

    private static class UdpSessionInfo {
        final String sessionId;
        final long createTime;
        UdpSessionInfo(String sessionId, long createTime) {
            this.sessionId = sessionId;
            this.createTime = createTime;
        }
    }
}
