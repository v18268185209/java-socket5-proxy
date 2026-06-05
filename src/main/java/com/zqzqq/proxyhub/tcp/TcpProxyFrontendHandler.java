package com.zqzqq.proxyhub.tcp;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.model.ProxyProtocol;
import com.zqzqq.proxyhub.core.model.SessionStatus;
import com.zqzqq.proxyhub.core.net.NettyTuningSupport;
import com.zqzqq.proxyhub.core.net.RelayBridgeHandler;
import com.zqzqq.proxyhub.core.security.AuthService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP 代理前端处理器
 * 支持：
 * 1. CONNECT 风格：CONNECT <host>:<port> HTTP/1.1\r\n
 * 2. 明文认证：AUTH <user>:<pass>\r\n
 * 3. 无认证直接隧道转发
 */
public class TcpProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpProxyFrontendHandler.class);
    private static final String CONNECT_CMD = "CONNECT";

    private final EventLoopGroup workerGroup;
    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final AuthService authService;
    private final String sessionId;
    private boolean authenticated;

    private Channel targetChannel;
    private String remoteHost;
    private int remotePort;

    public TcpProxyFrontendHandler(
            EventLoopGroup workerGroup,
            ProxyProperties properties,
            ProxyMetricsService metricsService,
            AccessControlService accessControlService,
            AuthService authService) {
        this.workerGroup = workerGroup;
        this.properties = properties;
        this.metricsService = metricsService;
        this.accessControlService = accessControlService;
        this.authService = authService;
        this.sessionId = metricsService.openSession(
                ProxyProtocol.SOCKS5,
                "TCP:" + properties.getTcp().getPort(),
                "",
                "tcp-proxy");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String remoteAddr = ctx.channel().remoteAddress().toString();
        ctx.writeAndFlush(Unpooled.copiedBuffer("TCP proxy ready. CONNECT host:port\r\n", CharsetUtil.UTF_8));
        log.info("TCP proxy connection from {}", remoteAddr);
        metricsService.addEvent("INFO", "TCP connection from " + remoteAddr);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof io.netty.buffer.ByteBuf buf)) {
            return;
        }

        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        String text = new String(data, CharsetUtil.UTF_8);

        if (remoteHost == null) {
            parseConnect(ctx, text);
            return;
        }

        if (targetChannel != null && targetChannel.isActive()) {
            targetChannel.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                    metricsService.recordFromClient(sessionId, data.length);
                }
            });
        }
    }

    private void parseConnect(ChannelHandlerContext ctx, String text) {
        String[] parts = text.trim().split(" ");
        if (parts.length < 2 || !parts[0].equals(CONNECT_CMD)) {
            sendError(ctx, "400 Bad request, use CONNECT host:port\r\n");
            return;
        }

        String[] hp = parts[1].split(":", 2);
        if (hp.length != 2) {
            sendError(ctx, "400 Bad target format\r\n");
            return;
        }

        try {
            int port = Integer.parseInt(hp[1]);
            connectTarget(ctx, hp[0], port);
        } catch (NumberFormatException e) {
            sendError(ctx, "400 Bad port\r\n");
        }
    }

    private void connectTarget(ChannelHandlerContext ctx, String targetHost, int targetPort) {
        if (!accessControlService.isTargetAllowed(targetHost, targetPort)) {
            metricsService.recordFailure("ACL_DENIED", "TCP ACL blocked " + targetHost + ":" + targetPort);
            sendError(ctx, "403 Target blocked by ACL\r\n");
            return;
        }

        remoteHost = targetHost;
        remotePort = targetPort;

        log.info("TCP proxy connecting to {}:{}", targetHost, targetPort);
        metricsService.addEvent("INFO", "TCP proxy to " + targetHost + ":" + targetPort);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NettyTuningSupport.clientChannelClass(properties))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getPerformance().getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new ReadTimeoutHandler(properties.getPerformance().getIdleTimeoutSeconds()));
                        ch.pipeline().addLast(new RelayBridgeHandler(
                                ctx.channel(),
                                metricsService,
                                sessionId,
                                false));
                    }
                });

        bootstrap.connect(targetHost, targetPort)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        targetChannel = future.channel();
                        targetChannel.pipeline().addLast(new ReadTimeoutHandler(properties.getPerformance().getIdleTimeoutSeconds()));
                        targetChannel.pipeline().addLast(new RelayBridgeHandler(
                                ctx.channel(),
                                metricsService,
                                sessionId,
                                true));
                        sendSuccess(ctx);
                        log.info("TCP proxy tunnel established to {}:{}", targetHost, targetPort);
                    } else {
                        metricsService.closeSession(sessionId, SessionStatus.FAILED_CONNECT);
                        metricsService.recordFailure("UPSTREAM_CONNECT_FAILED",
                                "TCP failed to " + targetHost + ":" + targetPort);
                        sendError(ctx, "502 Cannot connect to " + targetHost + ":" + targetPort + "\r\n");
                    }
                });
    }

    private void sendSuccess(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.copiedBuffer("200 OK\r\n\r\n", CharsetUtil.UTF_8));
    }

    private void sendError(ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush(Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
        if (ctx.channel().isActive()) {
            ctx.channel().close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (targetChannel != null) {
            targetChannel.close();
        }
        metricsService.closeSession(sessionId, SessionStatus.CLOSED);
        metricsService.addEvent("INFO", "TCP proxy session closed: " + sessionId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("TCP proxy exception from {}", ctx.channel().remoteAddress(), cause);
        metricsService.recordFailure("CLIENT_IO_ERROR", cause.getMessage());
        if (ctx.channel().isActive()) {
            ctx.channel().close();
        }
    }
}
