package com.zqzqq.proxyhub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints a security banner when the application is ready.
 */
@Component
class SecurityBanner {

    private static final Logger log = LoggerFactory.getLogger(SecurityBanner.class);

    @EventListener(ApplicationReadyEvent.class)
    public void print() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║              ProxyHub Security Notice                     ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║  ⚠️  Please verify the following:                        ║");
        System.out.println("║                                                          ║");
        System.out.println("║  1. Change default passwords (admin/admin → strong)       ║");
        System.out.println("║  2. Review ACL rules and management CIDRs                ║");
        System.out.println("║  3. Configure production TLS if exposed to internet       ║");
        System.out.println("║  4. Review audit log paths and retention                 ║");
        System.out.println("║                                                          ║");
        System.out.println("║  See documentation for hardening guide.                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
