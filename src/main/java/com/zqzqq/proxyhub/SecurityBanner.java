package com.zqzqq.proxyhub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Displays security hardening banner at application startup.
 * Only prints username, never password in startup logs.
 */
@Component
@Order(1)
public class SecurityBanner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityBanner.class);

    @Override
    public void run(String... args) {
        // Only log once at debug level - banner prints to console anyway
        log.debug("ProxyHub started - review security settings");
    }
}
