package com.zqzqq.proxyhub.core;

public interface ReloadableProxyServer extends ProxyServer {

    /**
     * Reload runtime configuration without full process restart.
     *
     * @return true if reload succeeds or no-op by design, false otherwise
     */
    boolean reload();
}
