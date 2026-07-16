package com.smartlibrary.web.superadmin;

import com.smartlibrary.service.SuperAdminDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Centralized Super Admin dashboard for the Library Management System.
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
        return "super-admin/dashboard";
    }
}
