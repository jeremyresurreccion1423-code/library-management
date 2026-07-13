package com.smartlibrary.web.superadmin;

import com.smartlibrary.service.SuperAdminDashboardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/super-admin")
public class SuperAdminStatsApiController {

    private final SuperAdminDashboardService superAdminDashboardService;
    private final String ssoSecret;

    public SuperAdminStatsApiController(
            SuperAdminDashboardService superAdminDashboardService,
            @Value("${super-admin.sso-secret}") String ssoSecret) {
        this.superAdminDashboardService = superAdminDashboardService;
        this.ssoSecret = ssoSecret;
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<Map<String, Object>> dashboardStats(
            @RequestHeader(value = "X-Super-Admin-Secret", required = false) String secret) {
        if (secret == null || !secret.equals(ssoSecret)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(superAdminDashboardService.getLibraryStatsForApi());
    }
}
