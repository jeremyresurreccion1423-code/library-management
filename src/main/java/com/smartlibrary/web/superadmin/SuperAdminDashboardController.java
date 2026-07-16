package com.smartlibrary.web.superadmin;

import com.smartlibrary.service.SuperAdminDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Centralized Super Admin dashboard. Provides a single hub of links into every
 * admin-only feature of the Library Management System (native, same app) and
 * every admin-only feature of the Attendance Management System (via SSO bridge links).
 * No student, teacher, or other end-user functionality is exposed here.
 */
@Controller
public class SuperAdminDashboardController {

    private final SuperAdminDashboardService superAdminDashboardService;

    public SuperAdminDashboardController(SuperAdminDashboardService superAdminDashboardService) {
        this.superAdminDashboardService = superAdminDashboardService;
    }

    @GetMapping("/super-admin")
    public String dashboard(Model model) {
        model.addAllAttributes(superAdminDashboardService.getCombinedDashboard());
        return "superadmin/dashboard";
    }

    @GetMapping("/super-admin/users")
    public String users() {
        return "superadmin/users";
    }
}
