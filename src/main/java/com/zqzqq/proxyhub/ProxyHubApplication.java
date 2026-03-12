package com.zqzqq.proxyhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProxyHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProxyHubApplication.class, args);
    }
}
