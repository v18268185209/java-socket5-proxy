package com.zqzqq.proxyhub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Displays security hardening banner at application startup.
 */
@Component
@Order(1)
public class SecurityBanner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityBanner.class);

    @Override
    public void run(String... args) {
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════╗\n" +
            "║              ProxyHub Security Notice                    ║\n" +
            "║                                                          ║\n" +
            "║  1. Change default credentials immediately!              ║\n" +
            "║  2. Configure management CIDR whitelist                  ║\n" +
            "║  3. Review ACL port restrictions                         ║\n" +
            "║  4. Enable TLS for management interface in prod          ║\n" +
            "║  5. Set proxy.acl.deny-target-ports for dangerous ports  ║\n" +
            "╚══════════════════════════════════════════════════════════╝\n");
    }
}
