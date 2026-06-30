package com.smartlibrary.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
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
        return "redirect:" + request.getRequestURI();
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex, HttpServletRequest request, RedirectAttributes ra) {
        logger.warn("Business rule violation at {}: {}", request.getRequestURI(), ex.getMessage());
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:" + request.getRequestURI();
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
    public String handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request, RedirectAttributes ra) {
        logger.error("Database integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
        ra.addFlashAttribute("error", "Operation failed due to a data conflict. Please check for duplicate entries.");
        return "redirect:" + request.getRequestURI();
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, HttpServletRequest request, RedirectAttributes ra) {
        logger.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ra.addFlashAttribute("error", "An unexpected error occurred. Please try again or contact support.");
        return "redirect:" + request.getRequestURI();
    }
}
