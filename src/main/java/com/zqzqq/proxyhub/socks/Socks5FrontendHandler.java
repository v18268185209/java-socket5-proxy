package com.zqzqq.proxyhub.socks;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.model.ProxyProtocol;
import com.zqzqq.proxyhub.core.model.SessionStatus;
import com.zqzqq.proxyhub.core.net.NetAddressUtils;
import com.zqzqq.proxyhub.core.net.NettyTuningSupport;
import com.zqzqq.proxyhub.core.net.RelayBridgeHandler;
import com.zqzqq.proxyhub.core.security.AuthService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.timeout.IdleStateEvent;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Socks5FrontendHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(Socks5FrontendHandler.class);

    private final EventLoopGroup workerGroup;
    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    private String sessionId;
    private String clientIp;
    private boolean connectionSlotAcquired;

    public Socks5FrontendHandler(
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
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Socks5InitialRequest request) {
            handleInitialRequest(ctx, request);
            return;
        }

        if (msg instanceof Socks5PasswordAuthRequest request) {
            handlePasswordAuthRequest(ctx, request);
            return;
        }

        if (msg instanceof Socks5CommandRequest request) {
            handleCommandRequest(ctx, request);
            return;
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            closeSessionIfNeeded(SessionStatus.CLOSED);
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeSessionIfNeeded(SessionStatus.CLOSED);
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.info("SOCKS5 frontend error from {}: {}",
                NetAddressUtils.address(ctx.channel().remoteAddress()),
                cause == null ? "unknown" : cause.toString());
        metricsService.recordFailure(ProxyFailureReason.CLIENT_IO_ERROR,
                "SOCKS5 frontend exception from " + NetAddressUtils.address(ctx.channel().remoteAddress())
                        + ": " + (cause == null ? "unknown" : cause.getClass().getSimpleName()));
        closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
        ctx.close();
    }

    private void handleInitialRequest(ChannelHandlerContext ctx, Socks5InitialRequest request) {
        String remote = NetAddressUtils.address(ctx.channel().remoteAddress());
        log.info("SOCKS5 initial request from {} methods={} authRequired={}",
                remote,
                request.authMethods(),
                authService.isSocksAuthRequired());
        if (request.version() != SocksVersion.SOCKS5) {
            log.info("SOCKS5 rejected client {} due to unsupported version: {}", remote, request.version());
            ctx.close();
            return;
        }

        if (authService.isSocksAuthRequired()) {
            if (request.authMethods().contains(Socks5AuthMethod.PASSWORD)) {
                ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
                replaceDecoder(ctx, Socks5PasswordAuthRequestDecoder.class);
                return;
            }
            log.info("SOCKS5 rejected client {}: auth required but PASSWORD method not offered (methods={})", remote, request.authMethods());
            metricsService.markAuthFailure();
            metricsService.recordFailure(ProxyFailureReason.AUTH_METHOD_UNACCEPTED,
                    "SOCKS5 auth required but PASSWORD not offered by client=" + remote);
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED)).addListener(f -> ctx.close());
            return;
        }

        if (request.authMethods().contains(Socks5AuthMethod.NO_AUTH)) {
            ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
            replaceDecoder(ctx, Socks5CommandRequestDecoder.class);
            return;
        }

        log.info("SOCKS5 rejected client {}: NO_AUTH method not offered (methods={})", remote, request.authMethods());
        metricsService.markAuthFailure();
        metricsService.recordFailure(ProxyFailureReason.AUTH_METHOD_UNACCEPTED,
                "SOCKS5 client=" + remote + " did not offer NO_AUTH");
        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED)).addListener(f -> ctx.close());
    }

    private void handlePasswordAuthRequest(ChannelHandlerContext ctx, Socks5PasswordAuthRequest request) {
        log.info("SOCKS5 password auth request from {} username={}",
                NetAddressUtils.address(ctx.channel().remoteAddress()),
                request.username());
        boolean ok = authService.validateSocksUserPassword(request.username(), request.password());
        if (!ok) {
            log.info("SOCKS5 auth failed for user '{}' from {}", request.username(), NetAddressUtils.address(ctx.channel().remoteAddress()));
            metricsService.markAuthFailure();
            metricsService.recordFailure(ProxyFailureReason.AUTH_INVALID_CREDENTIALS,
                    "SOCKS5 auth failed for user=" + request.username() + " client=" + NetAddressUtils.address(ctx.channel().remoteAddress()));
            ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.FAILURE))
                    .addListener(f -> ctx.close());
            return;
        }
        ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
        replaceDecoder(ctx, Socks5CommandRequestDecoder.class);
    }

    private void handleCommandRequest(ChannelHandlerContext ctx, Socks5CommandRequest request) {
        if (request.type() != io.netty.handler.codec.socksx.v5.Socks5CommandType.CONNECT) {
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.COMMAND_UNSUPPORTED, request.dstAddrType()))
                    .addListener(f -> ctx.close());
            return;
        }

        clientIp = NetAddressUtils.ip(ctx.channel().remoteAddress());
        String targetHost = request.dstAddr();
        int targetPort = request.dstPort();
        log.info("SOCKS5 connect request client={} target={}:{}", clientIp, targetHost, targetPort);

        if (!accessControlService.isClientAllowed(clientIp) || !accessControlService.isTargetAllowed(targetHost, targetPort)) {
            log.info("SOCKS5 ACL denied client={} target={}:{}", clientIp, targetHost, targetPort);
            metricsService.markBlocked();
            metricsService.recordFailure(ProxyFailureReason.ACL_DENIED,
                    "SOCKS5 ACL denied client=" + clientIp + " target=" + targetHost + ":" + targetPort);
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, request.dstAddrType()))
                    .addListener(f -> ctx.close());
            return;
        }
        if (!accessControlService.tryAcquireClientConnection(clientIp)) {
            log.info("SOCKS5 rate limited client={} because max-connections-per-client reached", clientIp);
            metricsService.markBlocked();
            metricsService.recordFailure(ProxyFailureReason.CONNECTION_QUOTA_EXCEEDED,
                    "SOCKS5 connection quota exceeded for client=" + clientIp);
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, request.dstAddrType()))
                    .addListener(f -> ctx.close());
            return;
        }
        connectionSlotAcquired = true;

        String targetAddress = targetHost + ":" + targetPort;
        sessionId = metricsService.openSession(
                ProxyProtocol.SOCKS5,
                "SOCKS5",
                NetAddressUtils.address(ctx.channel().remoteAddress()),
                targetAddress);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext remoteCtx) {
                        remoteCtx.pipeline().addLast(NettyTuningSupport.newFlushConsolidationHandler());
                        remoteCtx.pipeline().addLast(new RelayBridgeHandler(ctx.channel(), metricsService, sessionId, false));
                        remoteCtx.pipeline().remove(this);
                    }
                });
        NettyTuningSupport.applyClientOptions(bootstrap, properties);

        bootstrap.connect(createRemoteAddress(targetHost, targetPort)).addListener(connectFuture -> {
            if (!connectFuture.isSuccess()) {
                log.info("SOCKS5 upstream connect failed target={} client={} cause={}",
                        targetAddress,
                        clientIp,
                        connectFuture.cause() == null ? "unknown" : connectFuture.cause().toString());
                metricsService.markConnectFailure();
                metricsService.recordFailure(ProxyFailureReason.UPSTREAM_CONNECT_FAILED,
                        "SOCKS5 upstream connect failed target=" + targetAddress + " client=" + clientIp);
                closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.HOST_UNREACHABLE, request.dstAddrType()))
                        .addListener(f -> ctx.close());
                return;
            }

            Channel remoteChannel = ((io.netty.channel.ChannelFuture) connectFuture).channel();
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    request.dstAddrType(),
                    request.dstAddr(),
                    request.dstPort())).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    log.info("Failed to write SOCKS5 success response: {}",
                            writeFuture.cause() == null ? "unknown" : writeFuture.cause().toString());
                    metricsService.recordFailure(ProxyFailureReason.UPSTREAM_WRITE_FAILED,
                            "Failed to write SOCKS5 success response target=" + targetAddress + " client=" + clientIp);
                    closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                    remoteChannel.close();
                    ctx.close();
                    return;
                }

                if (!ctx.channel().isActive() || ctx.pipeline().context(this) == null) {
                    closeSessionIfNeeded(SessionStatus.CLOSED);
                    remoteChannel.close();
                    return;
                }

                removeHandlerIfExists(ctx, Socks5InitialRequestDecoder.class);
                removeHandlerIfExists(ctx, Socks5PasswordAuthRequestDecoder.class);
                removeHandlerIfExists(ctx, Socks5CommandRequestDecoder.class);
                removeHandlerIfExists(ctx, io.netty.handler.codec.socksx.v5.Socks5ServerEncoder.class);
                ctx.pipeline().replace(this, "socks5-relay", new RelayBridgeHandler(remoteChannel, metricsService, sessionId, true));
                remoteChannel.closeFuture().addListener(f -> closeSessionIfNeeded(SessionStatus.CLOSED));
            });
        });
    }

    private void replaceDecoder(ChannelHandlerContext ctx, Class<? extends ChannelHandler> decoderType) {
        removeHandlerIfExists(ctx, Socks5InitialRequestDecoder.class);
        removeHandlerIfExists(ctx, Socks5PasswordAuthRequestDecoder.class);
        removeHandlerIfExists(ctx, Socks5CommandRequestDecoder.class);
        ctx.pipeline().addFirst(newDecoder(decoderType));
    }

    private ChannelHandler newDecoder(Class<? extends ChannelHandler> decoderType) {
        if (decoderType == Socks5PasswordAuthRequestDecoder.class) {
            return new Socks5PasswordAuthRequestDecoder();
        }
        if (decoderType == Socks5CommandRequestDecoder.class) {
            return new Socks5CommandRequestDecoder();
        }
        throw new IllegalArgumentException("Unsupported decoder type: " + decoderType.getName());
    }

    private void removeHandlerIfExists(ChannelHandlerContext ctx, Class<? extends ChannelHandler> type) {
        ChannelHandler handler = ctx.pipeline().get(type);
        if (handler != null) {
            ctx.pipeline().remove(handler);
        }
    }

    private InetSocketAddress createRemoteAddress(String host, int port) {
        // Avoid blocking DNS lookup on the current event loop thread.
        return InetSocketAddress.createUnresolved(host, port);
    }

    private void closeSessionIfNeeded(SessionStatus status) {
        if (sessionId != null) {
            metricsService.closeSession(sessionId, status);
            sessionId = null;
        }
        releaseConnectionSlotIfNeeded();
    }

    private void releaseConnectionSlotIfNeeded() {
        if (connectionSlotAcquired) {
            accessControlService.releaseClientConnection(clientIp);
            connectionSlotAcquired = false;
        }
    }
}
