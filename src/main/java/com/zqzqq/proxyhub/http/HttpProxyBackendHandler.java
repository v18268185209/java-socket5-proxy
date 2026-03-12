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

public class HttpProxyBackendHandler extends ChannelInboundHandlerAdapter {

    private final Channel clientChannel;
    private final ProxyMetricsService metricsService;
    private final String sessionId;

    public HttpProxyBackendHandler(Channel clientChannel, ProxyMetricsService metricsService, String sessionId) {
        this.clientChannel = clientChannel;
        this.metricsService = metricsService;
        this.sessionId = sessionId;
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

        boolean closeAfterWrite = msg instanceof LastHttpContent;
        if (closeAfterWrite) {
            clientChannel.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    closeBoth(ctx.channel());
                    return;
                }
                closeBoth(ctx.channel());
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
