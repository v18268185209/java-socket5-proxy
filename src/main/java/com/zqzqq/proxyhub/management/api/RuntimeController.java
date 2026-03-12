package com.zqzqq.proxyhub.management.api;

import com.zqzqq.proxyhub.core.ProxyRuntimeManager;
import com.zqzqq.proxyhub.core.ProxyServer;
import com.zqzqq.proxyhub.management.dto.RuntimeSelfCheckResponse;
import com.zqzqq.proxyhub.management.dto.RuntimeStatusResponse;
import com.zqzqq.proxyhub.management.service.RuntimeSelfCheckService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runtime")
public class RuntimeController {

    private final ProxyRuntimeManager runtimeManager;
    private final List<ProxyServer> proxyServers;
    private final RuntimeSelfCheckService runtimeSelfCheckService;

    public RuntimeController(
            ProxyRuntimeManager runtimeManager,
            List<ProxyServer> proxyServers,
            RuntimeSelfCheckService runtimeSelfCheckService) {
        this.runtimeManager = runtimeManager;
        this.proxyServers = proxyServers;
        this.runtimeSelfCheckService = runtimeSelfCheckService;
    }

    @GetMapping("/status")
    public RuntimeStatusResponse status() {
        return new RuntimeStatusResponse(
                runtimeManager.isRunning(),
                proxyServers.stream().map(s -> new RuntimeStatusResponse.ListenerStatus(s.name(), s.isRunning())).toList());
    }

    @GetMapping("/self-check")
    public RuntimeSelfCheckResponse selfCheck(@RequestParam(name = "top", defaultValue = "8") int top) {
        return runtimeSelfCheckService.snapshot(top);
    }

    @PostMapping("/start")
    public RuntimeStatusResponse start() {
        runtimeManager.startAll();
        return status();
    }

    @PostMapping("/stop")
    public RuntimeStatusResponse stop() {
        runtimeManager.stopAll();
        return status();
    }

    @PostMapping("/restart")
    public RuntimeStatusResponse restart() {
        runtimeManager.restartAll();
        return status();
    }

    @PostMapping("/reload")
    public RuntimeStatusResponse reload() {
        runtimeManager.reloadAll();
        return status();
    }
}
