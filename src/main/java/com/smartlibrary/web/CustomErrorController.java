package com.smartlibrary.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorController.class);

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object statusObj = request.getAttribute("jakarta.servlet.error.status_code");
        int status = statusObj != null ? (Integer) statusObj : 500;
        String message = (String) request.getAttribute("jakarta.servlet.error.message");
        String path = (String) request.getAttribute("jakarta.servlet.error.request_uri");

        logger.warn("Error handling: status={}, path={}, message={}", status, path, message);

        model.addAttribute("status", status);
        model.addAttribute("error", "Error");
        model.addAttribute("message", message != null ? message : "An error occurred");
        model.addAttribute("path", path);
        
        return "error";
    }
}
