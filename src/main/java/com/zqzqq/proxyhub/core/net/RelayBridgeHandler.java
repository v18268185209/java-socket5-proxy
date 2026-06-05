package com.zqzqq.proxyhub.core.net;

import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayBridgeHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RelayBridgeHandler.class);

    private final Channel targetChannel;
    private final ProxyMetricsService metricsService;
    private final String sessionId;
    private final boolean fromClient;

    public RelayBridgeHandler(Channel targetChannel, ProxyMetricsService metricsService, String sessionId, boolean fromClient) {
        this.targetChannel = targetChannel;
        this.metricsService = metricsService;
        this.sessionId = sessionId;
        this.fromClient = fromClient;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!targetChannel.isActive()) {
            releaseAndClose(msg, ctx.channel());
            return;
        }
        long bytes = 0;
        if (msg instanceof ByteBuf byteBuf) {
            bytes = byteBuf.readableBytes();
        }
        ChannelFuture future = targetChannel.write(msg);
        final long forwardedBytes = bytes;
        future.addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                log.debug("Relay write failed", f.cause());
                closePair(ctx.channel());
            } else if (forwardedBytes > 0) {
                if (fromClient) {
                    metricsService.recordFromClient(sessionId, forwardedBytes);
                } else {
                    metricsService.recordFromTarget(sessionId, forwardedBytes);
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closePair(ctx.channel());
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (targetChannel.isActive()) {
            targetChannel.flush();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.debug("Relay bridge exception", cause);
        closePair(ctx.channel());
    }

    private void releaseAndClose(Object msg, Channel channel) {
        ReferenceCountUtil.release(msg);
        closePair(channel);
    }

    private void closePair(Channel sourceChannel) {
        closeOnFlush(sourceChannel);
        closeOnFlush(targetChannel);
    }

    private static void closeOnFlush(Channel channel) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(channel.alloc().buffer(0)).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
