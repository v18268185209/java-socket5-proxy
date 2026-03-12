package com.zqzqq.proxyhub.management.api;

import com.zqzqq.proxyhub.config.MenuOpsProperties;
import com.zqzqq.proxyhub.config.ProxyProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final ProxyProperties properties;
    private final MenuOpsProperties menuOpsProperties;

    public DashboardController(ProxyProperties properties, MenuOpsProperties menuOpsProperties) {
        this.properties = properties;
        this.menuOpsProperties = menuOpsProperties;
    }

    @GetMapping({"/", "/dashboard", "/self-check"})
    public String dashboard(Model model) {
        model.addAttribute("refreshSeconds", properties.getDashboard().getRefreshSeconds());
        model.addAttribute("timezone", properties.getDashboard().getTimezone());
        model.addAttribute("socksHost", properties.getSocks().getBindHost());
        model.addAttribute("socksPort", properties.getSocks().getPort());
        model.addAttribute("socksAuthEnabled", properties.getSocks().getAuth().isEnabled());
        model.addAttribute("httpHost", properties.getHttp().getBindHost());
        model.addAttribute("httpPort", properties.getHttp().getPort());
        model.addAttribute("httpAuthEnabled", properties.getHttp().getAuth().isEnabled());
        model.addAttribute("menuOpsEnabled", menuOpsProperties.isEnabled());
        model.addAttribute("menuOpsWorkdir", menuOpsProperties.getWorkdir());
        model.addAttribute("menuOpsAllowDestructive", menuOpsProperties.isAllowDestructive());
        model.addAttribute("menuOpsAllowRemoteScripts", menuOpsProperties.isAllowRemoteScripts());
        model.addAttribute("menuOpsAuditLogPath", menuOpsProperties.getAuditLogPath());
        model.addAttribute("menuOpsAuditDbEnabled", menuOpsProperties.isAuditDbEnabled());
        model.addAttribute("menuOpsAuditDbPath", menuOpsProperties.getAuditDbPath());
        model.addAttribute("menuOpsAuditDbRetentionDays", menuOpsProperties.getAuditDbRetentionDays());
        model.addAttribute("menuOpsAuditDbMaxRows", menuOpsProperties.getAuditDbMaxRows());
        model.addAttribute("menuOpsDiagnosticsDir", menuOpsProperties.getDiagnosticsDir());
        model.addAttribute("menuOpsDiagnosticsRetentionDays", menuOpsProperties.getDiagnosticsRetentionDays());
        model.addAttribute("menuOpsDiagnosticsMaxFiles", menuOpsProperties.getDiagnosticsMaxFiles());
        model.addAttribute("menuOpsDiagnosticsMaxDownloadBytes", menuOpsProperties.getDiagnosticsMaxDownloadBytes());
        model.addAttribute("menuOpsAutoSwitchEndpoints", menuOpsProperties.getAutoSwitchEndpoints());
        model.addAttribute("menuOpsAutoSwitchStacks", menuOpsProperties.getAutoSwitchStacks());
        return "dashboard";
    }
}
