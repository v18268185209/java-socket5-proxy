package com.zqzqq.proxyhub.http;

import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;

/**
 * Backend handler that forwards proxy responses back to the client.
 *
 * FIX P1: Supports keep-alive mode where LastHttpContent does NOT close the connection.
 */
public class HttpProxyBackendHandler extends ChannelInboundHandlerAdapter {

    private final Channel clientChannel;
    private final ProxyMetricsService metricsService;
    private final String sessionId;
    private final boolean keepAlive;

    public HttpProxyBackendHandler(Channel clientChannel, ProxyMetricsService metricsService, String sessionId) {
        this(clientChannel, metricsService, sessionId, false);
    }

    public HttpProxyBackendHandler(Channel clientChannel, ProxyMetricsService metricsService, String sessionId, boolean keepAlive) {
        this.clientChannel = clientChannel;
        this.metricsService = metricsService;
        this.sessionId = sessionId;
        this.keepAlive = keepAlive;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpContent content) {
            metricsService.recordFromTarget(sessionId, content.content().readableBytes());
        }

        if (!clientChannel.isActive()) {
            ReferenceCountUtil.release(msg);
            closeBoth(ctx.channel());
            return;
        }

        boolean lastContent = msg instanceof LastHttpContent;
        if (lastContent) {
            // FIX P1: Don't close keep-alive connections after LastHttpContent

            clientChannel.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    closeBoth(ctx.channel());
                    return;
                }
                if (!keepAlive) {
                    closeBoth(ctx.channel());
                }
            });
            return;
        }

        clientChannel.write(msg).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                closeBoth(ctx.channel());
            }
        });
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (clientChannel.isActive()) {
            clientChannel.flush();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeBoth(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        metricsService.recordFailure(ProxyFailureReason.CLIENT_IO_ERROR,
                "HTTP backend exception: " + (cause == null ? "unknown" : cause.getClass().getSimpleName()));
        closeBoth(ctx.channel());
    }

    private void closeBoth(Channel backendChannel) {
        closeOnFlush(clientChannel);
        closeOnFlush(backendChannel);
    }

    private void closeOnFlush(Channel channel) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(channel.alloc().buffer(0)).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
