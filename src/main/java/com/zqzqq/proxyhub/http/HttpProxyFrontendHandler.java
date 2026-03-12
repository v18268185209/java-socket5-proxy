package com.zqzqq.proxyhub.http;

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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyFrontendHandler.class);

    private final EventLoopGroup workerGroup;
    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    private final Deque<Object> pendingOutboundMessages = new ArrayDeque<>();

    private Channel remoteChannel;
    private String sessionId;
    private String clientIp;
    private boolean connectionSlotAcquired;
    private boolean connectTunnelPending;
    private boolean httpForwardPending;
    private boolean backendReady;
    private long pendingRequestBytes;

    public HttpProxyFrontendHandler(
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
        if (msg instanceof HttpRequest request) {
            handleHttpRequest(ctx, request);
            return;
        }
        if (msg instanceof HttpContent content) {
            handleHttpContent(ctx, content);
            return;
        }
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (backendReady && remoteChannel != null && remoteChannel.isActive()) {
            remoteChannel.flush();
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            releasePendingOutboundMessages();
            closeRemoteChannel();
            closeSessionIfNeeded(SessionStatus.CLOSED);
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        releasePendingOutboundMessages();
        closeRemoteChannel();
        closeSessionIfNeeded(SessionStatus.CLOSED);
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("HTTP proxy frontend error", cause);
        metricsService.recordFailure(ProxyFailureReason.CLIENT_IO_ERROR,
                "HTTP frontend exception from " + NetAddressUtils.address(ctx.channel().remoteAddress()) + ": "
                        + (cause == null ? "unknown" : cause.getClass().getSimpleName()));
        releasePendingOutboundMessages();
        closeRemoteChannel();
        closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
        ctx.close();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest request) {
        if (hasRequestInProgress()) {
            metricsService.recordFailure(ProxyFailureReason.CLIENT_IO_ERROR,
                    "HTTP client attempted multiple in-flight requests from " + NetAddressUtils.address(ctx.channel().remoteAddress()));
            if (request instanceof FullHttpRequest fullRequest) {
                ReferenceCountUtil.release(fullRequest);
            }
            sendAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Only one in-flight request per connection is supported");
            return;
        }

        if (!authorize(request)) {
            metricsService.markAuthFailure();
            metricsService.recordFailure(ProxyFailureReason.AUTH_REQUIRED,
                    "HTTP proxy authentication required from " + NetAddressUtils.address(ctx.channel().remoteAddress()));
            if (request instanceof FullHttpRequest fullRequest) {
                ReferenceCountUtil.release(fullRequest);
            }
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED,
                    Unpooled.copiedBuffer("Proxy authentication required", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.PROXY_AUTHENTICATE, "Basic realm=proxy-hub");
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            ctx.writeAndFlush(response).addListener(f -> ctx.close());
            return;
        }

        TargetAddress target = resolveTarget(request);
        if (target == null) {
            metricsService.recordFailure(ProxyFailureReason.INVALID_TARGET,
                    "HTTP request target is invalid: " + request.uri());
            if (request instanceof FullHttpRequest fullRequest) {
                ReferenceCountUtil.release(fullRequest);
            }
            sendAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid proxy target");
            return;
        }

        clientIp = NetAddressUtils.ip(ctx.channel().remoteAddress());
        if (!accessControlService.isClientAllowed(clientIp) || !accessControlService.isTargetAllowed(target.host(), target.port())) {
            metricsService.markBlocked();
            metricsService.recordFailure(ProxyFailureReason.ACL_DENIED,
                    "HTTP ACL denied client=" + clientIp + " target=" + target.host() + ":" + target.port());
            if (request instanceof FullHttpRequest fullRequest) {
                ReferenceCountUtil.release(fullRequest);
            }
            sendAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Request blocked by ACL policy");
            return;
        }
        if (!accessControlService.tryAcquireClientConnection(clientIp)) {
            metricsService.markBlocked();
            metricsService.recordFailure(ProxyFailureReason.CONNECTION_QUOTA_EXCEEDED,
                    "HTTP connection quota exceeded for client=" + clientIp);
            if (request instanceof FullHttpRequest fullRequest) {
                ReferenceCountUtil.release(fullRequest);
            }
            sendAndClose(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, "Client connection quota exceeded");
            return;
        }
        connectionSlotAcquired = true;

        if (request.method() == HttpMethod.CONNECT) {
            connectTunnelPending = true;
            openConnectTunnel(ctx, target);
            if (request instanceof FullHttpRequest fullRequest) {
                ReferenceCountUtil.release(fullRequest);
            }
            return;
        }

        forwardHttpRequest(ctx, request, target);
    }

    private void handleHttpContent(ChannelHandlerContext ctx, HttpContent content) {
        if (connectTunnelPending) {
            ReferenceCountUtil.release(content);
            return;
        }

        if (!httpForwardPending && !backendReady) {
            ReferenceCountUtil.release(content);
            metricsService.recordFailure(ProxyFailureReason.CLIENT_IO_ERROR,
                    "Unexpected HTTP content without request from " + NetAddressUtils.address(ctx.channel().remoteAddress()));
            sendAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Unexpected HTTP content without request");
            return;
        }

        if (backendReady) {
            forwardHttpObject(ctx, content);
            return;
        }

        if (!enqueuePendingMessage(ctx, content, readableBytes(content))) {
            ReferenceCountUtil.release(content);
        }
    }

    private boolean authorize(HttpRequest request) {
        if (!authService.isHttpAuthRequired()) {
            return true;
        }
        String authHeader = request.headers().get(HttpHeaderNames.PROXY_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }
        String encoded = authHeader.substring("Basic ".length());
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), CharsetUtil.UTF_8);
        } catch (Exception ex) {
            return false;
        }
        int idx = decoded.indexOf(':');
        if (idx <= 0) {
            return false;
        }
        String username = decoded.substring(0, idx);
        String password = decoded.substring(idx + 1);
        return authService.validateHttpBasic(username, password);
    }

    private TargetAddress resolveTarget(HttpRequest request) {
        if (request.method() == HttpMethod.CONNECT) {
            return parseConnectTarget(request.uri());
        }

        try {
            URI uri = URI.create(request.uri());
            if (uri.getHost() != null) {
                int port = uri.getPort() > 0 ? uri.getPort() : 80;
                String path = uri.getRawPath();
                if (path == null || path.isEmpty()) {
                    path = "/";
                }
                if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
                    path = path + "?" + uri.getRawQuery();
                }
                return new TargetAddress(uri.getHost(), port, path);
            }
        } catch (Exception ignore) {
            // fall back to Host header below
        }

        String hostHeader = request.headers().get(HttpHeaderNames.HOST);
        if (hostHeader == null || hostHeader.isBlank()) {
            return null;
        }
        String host = hostHeader;
        int port = 80;
        int idx = hostHeader.lastIndexOf(':');
        if (idx > 0 && idx < hostHeader.length() - 1) {
            host = hostHeader.substring(0, idx);
            try {
                port = Integer.parseInt(hostHeader.substring(idx + 1));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return new TargetAddress(host, port, request.uri());
    }

    private void openConnectTunnel(ChannelHandlerContext ctx, TargetAddress target) {
        sessionId = metricsService.openSession(
                ProxyProtocol.HTTPS_TUNNEL,
                "HTTP",
                NetAddressUtils.address(ctx.channel().remoteAddress()),
                target.host() + ":" + target.port());

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NettyTuningSupport.clientChannelClass(properties))
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext remoteCtx) {
                        remoteCtx.pipeline().addLast(NettyTuningSupport.newFlushConsolidationHandler());
                        remoteCtx.pipeline().addLast(new RelayBridgeHandler(ctx.channel(), metricsService, sessionId, false));
                        remoteCtx.pipeline().remove(this);
                    }
                });
        NettyTuningSupport.applyClientOptions(bootstrap, properties);

        bootstrap.connect(createRemoteAddress(target.host(), target.port())).addListener(connectFuture -> {
            connectTunnelPending = false;
            if (!connectFuture.isSuccess()) {
                metricsService.markConnectFailure();
                metricsService.recordFailure(ProxyFailureReason.UPSTREAM_CONNECT_FAILED,
                        "HTTP CONNECT failed target=" + target.host() + ":" + target.port());
                closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                sendAndClose(ctx, HttpResponseStatus.BAD_GATEWAY, "CONNECT target unavailable");
                return;
            }

            Channel connectedRemoteChannel = ((ChannelFuture) connectFuture).channel();
            if (!ctx.channel().isActive()) {
                connectedRemoteChannel.close();
                closeSessionIfNeeded(SessionStatus.CLOSED);
                return;
            }

            remoteChannel = connectedRemoteChannel;
            FullHttpResponse ok = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "Connection Established"));
            ctx.writeAndFlush(ok).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    log.debug("Failed to write CONNECT success response", writeFuture.cause());
                    metricsService.recordFailure(ProxyFailureReason.UPSTREAM_WRITE_FAILED,
                            "Failed to write CONNECT success response for target=" + target.host() + ":" + target.port());
                    closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                    connectedRemoteChannel.close();
                    ctx.close();
                    return;
                }

                if (!ctx.channel().isActive() || ctx.pipeline().context(this) == null) {
                    closeSessionIfNeeded(SessionStatus.CLOSED);
                    connectedRemoteChannel.close();
                    return;
                }

                removeHttpCodec(ctx);
                ctx.pipeline().replace(this, "http-connect-relay", new RelayBridgeHandler(connectedRemoteChannel, metricsService, sessionId, true));
                connectedRemoteChannel.closeFuture().addListener(f -> closeSessionIfNeeded(SessionStatus.CLOSED));
            });
        });
    }

    private void forwardHttpRequest(ChannelHandlerContext ctx, HttpRequest request, TargetAddress target) {
        sessionId = metricsService.openSession(
                ProxyProtocol.HTTP,
                "HTTP",
                NetAddressUtils.address(ctx.channel().remoteAddress()),
                target.host() + ":" + target.port());

        httpForwardPending = true;
        backendReady = false;
        ctx.channel().config().setAutoRead(false);

        if (!enqueuePendingMessage(ctx, buildOutboundRequestHead(request, target), 0)) {
            if (request instanceof FullHttpRequest fullRequest) {
                ReferenceCountUtil.release(fullRequest);
            }
            return;
        }

        if (request instanceof FullHttpRequest fullRequest) {
            try {
                DefaultLastHttpContent lastContent = new DefaultLastHttpContent(fullRequest.content().retain());
                lastContent.trailingHeaders().set(fullRequest.trailingHeaders());
                if (!enqueuePendingMessage(ctx, lastContent, fullRequest.content().readableBytes())) {
                    ReferenceCountUtil.release(lastContent);
                    return;
                }
            } finally {
                ReferenceCountUtil.release(fullRequest);
            }
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NettyTuningSupport.clientChannelClass(properties))
                .handler(new io.netty.channel.ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(NettyTuningSupport.newFlushConsolidationHandler());
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpProxyBackendHandler(ctx.channel(), metricsService, sessionId));
                    }
                });
        NettyTuningSupport.applyClientOptions(bootstrap, properties);

        bootstrap.connect(createRemoteAddress(target.host(), target.port())).addListener(connectFuture -> {
            if (!connectFuture.isSuccess()) {
                releasePendingOutboundMessages();
                metricsService.markConnectFailure();
                metricsService.recordFailure(ProxyFailureReason.UPSTREAM_CONNECT_FAILED,
                        "HTTP forward connect failed target=" + target.host() + ":" + target.port());
                closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                sendAndClose(ctx, HttpResponseStatus.BAD_GATEWAY, "HTTP target unavailable");
                return;
            }

            Channel connectedRemoteChannel = ((ChannelFuture) connectFuture).channel();
            if (!ctx.channel().isActive()) {
                releasePendingOutboundMessages();
                connectedRemoteChannel.close();
                closeSessionIfNeeded(SessionStatus.CLOSED);
                return;
            }

            remoteChannel = connectedRemoteChannel;
            backendReady = true;
            httpForwardPending = false;
            flushPendingMessages(ctx);
            if (ctx.channel().isActive()) {
                ctx.channel().config().setAutoRead(true);
                ctx.read();
            }

            connectedRemoteChannel.closeFuture().addListener(f -> {
                backendReady = false;
                remoteChannel = null;
                closeSessionIfNeeded(SessionStatus.CLOSED);
            });
        });
    }

    private HttpRequest buildOutboundRequestHead(HttpRequest request, TargetAddress target) {
        String path = target.path();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        HttpRequest outbound = new DefaultHttpRequest(
                request.protocolVersion(),
                request.method(),
                path);
        outbound.headers().setAll(request.headers());
        outbound.headers().set(HttpHeaderNames.HOST, target.authorityHeader());
        outbound.headers().remove(HttpHeaderNames.PROXY_AUTHORIZATION);
        outbound.headers().remove("Proxy-Connection");
        outbound.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        HttpUtil.setKeepAlive(outbound, false);
        return outbound;
    }

    private boolean enqueuePendingMessage(ChannelHandlerContext ctx, Object msg, long readableBytes) {
        if (readableBytes > 0) {
            long newTotal = pendingRequestBytes + readableBytes;
            if (newTotal > properties.getPerformance().getHttpPendingRequestMaxBytes()) {
                releasePendingOutboundMessages();
                metricsService.recordFailure(ProxyFailureReason.HTTP_PENDING_BUFFER_EXCEEDED,
                        "HTTP pending request buffer exceeded for client=" + clientIp);
                closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                sendAndClose(ctx, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                        "Request body buffering limit exceeded while awaiting upstream connect");
                return false;
            }
            pendingRequestBytes = newTotal;
        }
        pendingOutboundMessages.addLast(msg);
        return true;
    }

    private void flushPendingMessages(ChannelHandlerContext ctx) {
        while (!pendingOutboundMessages.isEmpty()) {
            Object msg = pendingOutboundMessages.pollFirst();
            long bytes = readableBytes(msg);
            if (bytes > 0) {
                pendingRequestBytes -= bytes;
            }
            forwardHttpObject(ctx, msg);
        }
        if (remoteChannel != null && remoteChannel.isActive()) {
            remoteChannel.flush();
        }
        pendingRequestBytes = 0;
    }

    private void forwardHttpObject(ChannelHandlerContext ctx, Object msg) {
        Channel outboundChannel = remoteChannel;
        if (outboundChannel == null || !outboundChannel.isActive()) {
            ReferenceCountUtil.release(msg);
            metricsService.recordFailure(ProxyFailureReason.UPSTREAM_WRITE_FAILED,
                    "HTTP forward write failed because upstream channel is inactive");
            closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
            ctx.close();
            return;
        }

        long bytes = readableBytes(msg);
        if (bytes > 0) {
            metricsService.recordFromClient(sessionId, bytes);
        }

        outboundChannel.write(msg).addListener((ChannelFutureListener) writeFuture -> {
            if (!writeFuture.isSuccess()) {
                metricsService.recordFailure(ProxyFailureReason.UPSTREAM_WRITE_FAILED,
                        "HTTP forward write failed: " + (writeFuture.cause() == null ? "unknown" : writeFuture.cause().getClass().getSimpleName()));
                closeRemoteChannel();
                closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                if (ctx.channel().isActive()) {
                    ctx.close();
                }
            }
        });
    }

    private boolean hasRequestInProgress() {
        return connectTunnelPending || httpForwardPending || backendReady || sessionId != null || !pendingOutboundMessages.isEmpty();
    }

    private long readableBytes(Object msg) {
        if (msg instanceof HttpContent content) {
            return content.content().readableBytes();
        }
        return 0;
    }

    private void releasePendingOutboundMessages() {
        while (!pendingOutboundMessages.isEmpty()) {
            ReferenceCountUtil.release(pendingOutboundMessages.pollFirst());
        }
        pendingRequestBytes = 0;
        httpForwardPending = false;
    }

    private void closeRemoteChannel() {
        if (remoteChannel != null && remoteChannel.isActive()) {
            remoteChannel.close();
        }
        remoteChannel = null;
        backendReady = false;
        connectTunnelPending = false;
    }

    private void removeHttpCodec(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(HttpServerCodec.class) != null) {
            ctx.pipeline().remove(HttpServerCodec.class);
        }
    }

    private InetSocketAddress createRemoteAddress(String host, int port) {
        return InetSocketAddress.createUnresolved(host, port);
    }

    private TargetAddress parseConnectTarget(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }

        String host;
        String portText;
        if (uri.startsWith("[")) {
            int closeBracket = uri.indexOf(']');
            if (closeBracket <= 1 || closeBracket + 2 >= uri.length() || uri.charAt(closeBracket + 1) != ':') {
                return null;
            }
            host = uri.substring(1, closeBracket);
            portText = uri.substring(closeBracket + 2);
        } else {
            int lastColon = uri.lastIndexOf(':');
            if (lastColon <= 0 || lastColon >= uri.length() - 1) {
                return null;
            }
            if (uri.indexOf(':') != lastColon) {
                return null;
            }
            host = uri.substring(0, lastColon);
            portText = uri.substring(lastColon + 1);
        }

        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                return null;
            }
            return new TargetAddress(host, port, uri);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void sendAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(f -> ctx.close());
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

    private record TargetAddress(String host, int port, String path) {
        private String authorityHeader() {
            if (port == 80) {
                return host;
            }
            return host + ":" + port;
        }
    }
}
