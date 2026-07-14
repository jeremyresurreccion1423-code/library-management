package com.smartlibrary.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request, RedirectAttributes ra) {
        logger.warn("Validation error at {}: {}", request.getRequestURI(), ex.getMessage());
        ra.addFlashAttribute("error", ex.getMessage());
        return safeRedirect(request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex, HttpServletRequest request, RedirectAttributes ra) {
        logger.warn("Business rule violation at {}: {}", request.getRequestURI(), ex.getMessage());
        ra.addFlashAttribute("error", ex.getMessage());
        return safeRedirect(request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, RedirectAttributes ra) {
        logger.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        ra.addFlashAttribute("error", "You do not have permission to access this resource.");
        return "redirect:/";
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request, RedirectAttributes ra) {
        logger.warn("Optimistic locking conflict at {}: {}", request.getRequestURI(), ex.getMessage());
        ra.addFlashAttribute("error", "The data was modified by another user. Please try again.");
        return "redirect:" + request.getRequestURI();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request, Model model, RedirectAttributes ra) {
        logger.error("Database integrity violation at {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (path.startsWith("/register")) {
            String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
            String message = "Registration failed because of a duplicate entry.";
            if (detail != null) {
                String lower = detail.toLowerCase();
                if (lower.contains("username") || lower.contains("uk_public_users_username") || lower.contains("users_username")) {
                    message = "Username already taken. Please choose another username.";
                } else if (lower.contains("email") || lower.contains("uk_public_users_email") || lower.contains("users_email")) {
                    message = "An account with that email already exists.";
                } else if (lower.contains("student_id") || lower.contains("student_number")) {
                    message = "Student ID conflict. Please try registering again.";
                }
            }
            ra.addFlashAttribute("error", message);
            return "redirect:/register";
        }
        model.addAttribute("error", "Operation failed due to a data conflict. Please check for duplicate entries.");
        model.addAttribute("path", path);
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, HttpServletRequest request, Model model) {
        logger.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("error", "An unexpected error occurred. Please try again or contact support.");
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }

    private String safeRedirect(HttpServletRequest request) {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String uri = request.getRequestURI();
            if (uri.startsWith("/admin/books/save")) {
                return "redirect:/admin/books/new";
            }
            if (uri.startsWith("/admin/")) {
                return "redirect:/admin/books";
            }
            return "redirect:/";
        }
        return "redirect:" + request.getRequestURI();
    }
}
