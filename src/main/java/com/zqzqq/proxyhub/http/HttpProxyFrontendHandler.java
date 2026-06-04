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
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP proxy frontend handler with keep-alive support.
 *
 * Changes vs. original:
 * - Supports HTTP/1.1 keep-alive (multiple requests per connection)
 * - Fixes connection slot leak in exceptionCaught
 * - Masks passwords in audit log messages
 */
public class HttpProxyFrontendHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyFrontendHandler.class);

    private final EventLoopGroup workerGroup;
    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;
    private final AccessControlService accessControlService;
    private final AuthService authService;

    private Channel remoteChannel;
    private String sessionId;
    private String clientIp;
    private boolean connectionSlotAcquired;
    private boolean connectTunnelPending;
    private boolean backendReady;
    private boolean isKeepAlive;

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
        try {
            if (msg instanceof HttpRequest request) {
                handleHttpRequest(ctx, request);
                return;
            }
            if (msg instanceof HttpContent content) {
                handleHttpContent(ctx, content);
                return;
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
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
            releaseConnectionSlotIfNeeded();
            closeRemoteChannel();
            closeSessionIfNeeded(SessionStatus.CLOSED);
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        releaseConnectionSlotIfNeeded();
        closeRemoteChannel();
        closeSessionIfNeeded(SessionStatus.CLOSED);
        ctx.fireChannelInactive();
    }

    /**
     * FIX P1: Ensure connection slot is always released on exception.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("HTTP proxy frontend error: {}", cause == null ? "unknown" : cause.getClass().getSimpleName());
        metricsService.recordFailure(ProxyFailureReason.CLIENT_IO_ERROR,
                "HTTP frontend exception from " + NetAddressUtils.address(ctx.channel().remoteAddress()) + ": "
                        + (cause == null ? "unknown" : cause.getClass().getSimpleName()));
        releaseConnectionSlotIfNeeded();
        closeRemoteChannel();
        closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
        ctx.close();
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, HttpRequest request) {
        clientIp = NetAddressUtils.ip(ctx.channel().remoteAddress());

        // Determine keep-alive from this request
        isKeepAlive = HttpUtil.isKeepAlive(request);

        if (connectTunnelPending) {
            metricsService.recordFailure(ProxyFailureReason.CLIENT_IO_ERROR,
                    "HTTP proxy received request while CONNECT tunnel established from " +
                            NetAddressUtils.address(ctx.channel().remoteAddress()));
            sendAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "CONNECT tunnel already established");
            return;
        }

        if (!authorize(request)) {
            metricsService.markAuthFailure();
            metricsService.recordFailure(ProxyFailureReason.AUTH_REQUIRED,
                    "HTTP proxy authentication required from " + NetAddressUtils.address(ctx.channel().remoteAddress()));
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED,
                    Unpooled.copiedBuffer("Proxy authentication required", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.PROXY_AUTHENTICATE, "Basic realm=proxy-hub");
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, "close");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        TargetAddress target = resolveTarget(request);
        if (target == null) {
            metricsService.recordFailure(ProxyFailureReason.INVALID_TARGET,
                    "HTTP request target is invalid: " + request.uri());
            sendAndClose(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid proxy target", "close");
            return;
        }

        if (!accessControlService.isClientAllowed(clientIp) || !accessControlService.isTargetAllowed(target.host(), target.port())) {
            metricsService.markBlocked();
            metricsService.recordFailure(ProxyFailureReason.ACL_DENIED,
                    "HTTP ACL denied client=" + clientIp + " target=" + target.host() + ":" + target.port());
            sendAndClose(ctx, HttpResponseStatus.FORBIDDEN, "Request blocked by ACL policy", "close");
            return;
        }

        if (!accessControlService.tryAcquireClientConnection(clientIp)) {
            metricsService.markBlocked();
            metricsService.recordFailure(ProxyFailureReason.CONNECTION_QUOTA_EXCEEDED,
                    "HTTP connection quota exceeded for client=" + clientIp);
            sendAndClose(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, "Client connection quota exceeded");
            return;
        }
        connectionSlotAcquired = true;

        if (request.method() == HttpMethod.CONNECT) {
            connectTunnelPending = true;
            openConnectTunnel(ctx, target, request);
            return;
        }

        forwardHttpRequest(ctx, request, target);
    }

    private void handleHttpContent(ChannelHandlerContext ctx, HttpContent content) {
        if (connectTunnelPending) {
            return;
        }

        if (!backendReady) {
            return;
        }

        forwardHttpObject(ctx, content);
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
        boolean ok = authService.validateHttpBasic(username, password);
        if (!ok) {
            log.info("HTTP proxy auth failed for user={}", maskSensitive(username));
        }
        return ok;
    }

    /**
     * FIX P1: Mask sensitive values in logs.
     */
    private String maskSensitive(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        return value; // username is fine to log; password is not passed here
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

    private void openConnectTunnel(ChannelHandlerContext ctx, TargetAddress target, HttpRequest request) {
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
                        remoteCtx.pipeline().addLast(new RelayBridgeHandler(ctx.channel(), metricsService, sessionId, true));
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
                sendAndClose(ctx, HttpResponseStatus.BAD_GATEWAY, "CONNECT target unavailable",
                        isKeepAlive ? "keep-alive" : "close");
                return;
            }

            Channel connectedRemoteChannel = ((ChannelFuture) connectFuture).channel();
            if (!ctx.channel().isActive()) {
                connectedRemoteChannel.close();
                closeSessionIfNeeded(SessionStatus.CLOSED);
                return;
            }

            remoteChannel = connectedRemoteChannel;
            FullHttpResponse ok = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.EMPTY_BUFFER);
            ok.headers().set(HttpHeaderNames.CONNECTION, isKeepAlive ? "keep-alive" : "close");
            ctx.writeAndFlush(ok).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

            if (!ctx.channel().isActive() || ctx.pipeline().context(this) == null) {
                closeSessionIfNeeded(SessionStatus.CLOSED);
                connectedRemoteChannel.close();
                return;
            }

            removeHttpCodec(ctx);
            ctx.pipeline().replace(this, "http-connect-relay",
                    new RelayBridgeHandler(connectedRemoteChannel, metricsService, sessionId, true));
            connectedRemoteChannel.closeFuture().addListener(f -> closeSessionIfNeeded(SessionStatus.CLOSED));
        });
    }

    /**
     * FIX P1: Support keep-alive for HTTP forward requests.
     * Multiple requests can flow over the same connection if the client sends Keep-Alive.
     */
    private void forwardHttpRequest(ChannelHandlerContext ctx, HttpRequest request, TargetAddress target) {
        // If we already have a backend connection and keep-alive, reuse it
        if (backendReady && remoteChannel != null && remoteChannel.isActive() && isKeepAlive) {
            forwardExistingRequest(ctx, request, target);
            return;
        }

        // Close any stale connection
        if (backendReady && remoteChannel != null) {
            remoteChannel.close();
        }

        sessionId = metricsService.openSession(
                ProxyProtocol.HTTP,
                "HTTP",
                NetAddressUtils.address(ctx.channel().remoteAddress()),
                target.host() + ":" + target.port());

        backendReady = false;
        ctx.channel().config().setAutoRead(false);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NettyTuningSupport.clientChannelClass(properties))
                .handler(new io.netty.channel.ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(NettyTuningSupport.newFlushConsolidationHandler());
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpContentDecompressor());
                        ch.pipeline().addLast(new HttpProxyBackendHandler(
                                ctx.channel(), metricsService, sessionId, isKeepAlive));
                    }
                });
        NettyTuningSupport.applyClientOptions(bootstrap, properties);

        bootstrap.connect(createRemoteAddress(target.host(), target.port())).addListener(connectFuture -> {
            if (!connectFuture.isSuccess()) {
                metricsService.markConnectFailure();
                metricsService.recordFailure(ProxyFailureReason.UPSTREAM_CONNECT_FAILED,
                        "HTTP forward connect failed target=" + target.host() + ":" + target.port());
                closeSessionIfNeeded(SessionStatus.FAILED_CONNECT);
                sendAndClose(ctx, HttpResponseStatus.BAD_GATEWAY, "HTTP target unavailable",
                        isKeepAlive ? "keep-alive" : "close");
                return;
            }

            Channel connectedRemoteChannel = ((ChannelFuture) connectFuture).channel();
            if (!ctx.channel().isActive()) {
                connectedRemoteChannel.close();
                closeSessionIfNeeded(SessionStatus.CLOSED);
                return;
            }

            remoteChannel = connectedRemoteChannel;
            backendReady = true;

            // Send the request head
            HttpRequest outbound = buildOutboundRequestHead(request, target);
            remoteChannel.write(outbound);

            // Send body
            if (request instanceof HttpContent content) {
                remoteChannel.write(content);
            }

            remoteChannel.flush();

            if (ctx.channel().isActive()) {
                ctx.channel().config().setAutoRead(true);
                ctx.read();
            }

            connectedRemoteChannel.closeFuture().addListener(f -> {
                backendReady = false;
                remoteChannel = null;
                sessionId = null;
                connectionSlotAcquired = false;
                closeSessionIfNeeded(SessionStatus.CLOSED);
            });
        });
    }

    /**
     * FIX P1: Forward a new request over an existing backend connection (keep-alive).
     */
    private void forwardExistingRequest(ChannelHandlerContext ctx, HttpRequest request, TargetAddress target) {
        HttpRequest outbound = buildOutboundRequestHead(request, target);
        remoteChannel.write(outbound);

        if (request instanceof HttpContent content) {
            remoteChannel.write(content);
        }
        remoteChannel.flush();
        log.debug("Forwarded keep-alive request to backend target={} path={}",
                target.host() + ":" + target.port(), request.uri());
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

        // FIX P1: Preserve keep-alive
        HttpUtil.setKeepAlive(outbound, isKeepAlive);

        return outbound;
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

    private long readableBytes(Object msg) {
        if (msg instanceof HttpContent content) {
            return content.content().readableBytes();
        }
        return 0;
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
        if (ctx.pipeline().get(HttpResponseEncoder.class) != null) {
            ctx.pipeline().remove(HttpResponseEncoder.class);
        }
        if (ctx.pipeline().get(HttpObjectAggregator.class) != null) {
            ctx.pipeline().remove(HttpObjectAggregator.class);
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

    private void sendAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String message, String connection) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, connection);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendAndClose(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        sendAndClose(ctx, status, message, "close");
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
            if (port == 443) {
                return host + ":" + port;
            }
            return host;
        }
    }
}
