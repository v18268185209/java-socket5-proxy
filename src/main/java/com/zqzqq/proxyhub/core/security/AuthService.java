package com.zqzqq.proxyhub.core.security;

import com.zqzqq.proxyhub.config.ProxyProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final ProxyProperties properties;

    public AuthService(ProxyProperties properties) {
        this.properties = properties;
    }

    public boolean isSocksAuthRequired() {
        return properties.getSocks().getAuth().isEnabled();
    }

    public boolean isHttpAuthRequired() {
        return properties.getHttp().getAuth().isEnabled();
    }

    public boolean validateSocksUserPassword(String username, String password) {
        ProxyProperties.AuthProperties auth = properties.getSocks().getAuth();
        if (!auth.isEnabled()) {
            return true;
        }
        return auth.getUsername().equals(username) && auth.getPassword().equals(password);
    }

    public boolean validateHttpBasic(String username, String password) {
        ProxyProperties.AuthProperties auth = properties.getHttp().getAuth();
        if (!auth.isEnabled()) {
            return true;
        }
        return auth.getUsername().equals(username) && auth.getPassword().equals(password);
    }
}
