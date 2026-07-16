package com.smartlibrary.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class NavigationModelAdvice {

    @ModelAttribute
    public void addNavigationAttributes(HttpServletRequest request, Model model) {
        String uri = request != null ? request.getRequestURI() : "";
        model.addAttribute("navUri", uri);
        model.addAttribute("navLibOpen", uri.startsWith("/admin"));
        model.addAttribute("navDashboardActive", "/super-admin".equals(uri) || "/super-admin/".equals(uri));
        model.addAttribute("navSecurityActive", uri.startsWith("/super-admin/security"));
        model.addAttribute("navUsersActive", uri.startsWith("/super-admin/users"));
    }
}
