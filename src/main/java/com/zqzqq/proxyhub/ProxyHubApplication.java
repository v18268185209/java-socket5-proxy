package com.zqzqq.proxyhub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProxyHubApplication {

    private static final Logger log = LoggerFactory.getLogger(ProxyHubApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ProxyHubApplication.class);
        app.setAdditionalProfiles("production");
        ConfigurableApplicationContext ctx = app.run(args);
        
        // SecurityBanner runs automatically via CommandLineRunner
        Environment env = ctx.getEnvironment();
        int port = env.getProperty("server.port", Integer.class, 9090);
        log.info("ProxyHub started on port {}", port);
        log.info("Management UI: http://localhost:{}/actuator", port);
    }
}
