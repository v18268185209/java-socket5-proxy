package com.zqzqq.proxyhub.core.net;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class NetAddressUtils {

    private NetAddressUtils() {
    }

    public static String ip(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            return inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
        }
        return "unknown";
    }

    public static String address(SocketAddress address) {
        if (address instanceof InetSocketAddress inet) {
            String host = inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
            return host + ":" + inet.getPort();
        }
        return String.valueOf(address);
    }
}
