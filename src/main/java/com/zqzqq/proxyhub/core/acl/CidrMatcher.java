package com.zqzqq.proxyhub.core.acl;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class CidrMatcher {

    private final int network;
    private final int mask;

    public CidrMatcher(String cidr) {
        if (cidr == null || !cidr.contains("/")) {
            throw new IllegalArgumentException("Invalid CIDR: " + cidr);
        }
        String[] parts = cidr.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR: " + cidr);
        }
        int prefix = Integer.parseInt(parts[1]);
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Invalid prefix length: " + cidr);
        }
        this.mask = prefix == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix));
        this.network = ipv4ToInt(parts[0]) & this.mask;
    }

    public boolean matches(String ip) {
        try {
            int addr = ipv4ToInt(ip);
            return (addr & mask) == network;
        } catch (Exception ex) {
            return false;
        }
    }

    private int ipv4ToInt(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (!(address instanceof Inet4Address)) {
                throw new IllegalArgumentException("IPv6 is not supported in CIDR matcher: " + ip);
            }
            byte[] bytes = address.getAddress();
            return ((bytes[0] & 0xFF) << 24)
                    | ((bytes[1] & 0xFF) << 16)
                    | ((bytes[2] & 0xFF) << 8)
                    | (bytes[3] & 0xFF);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid ip: " + ip, e);
        }
    }
}
