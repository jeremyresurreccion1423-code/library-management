package com.smartlibrary.web.admin;

import com.smartlibrary.entity.User;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.LibraryUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/profile")
public class AdminProfileController {

    private final UserRepository userRepository;

    public AdminProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal LibraryUserDetails user, Model model) {
        model.addAttribute("username", user.getUsername());
        model.addAttribute("fullName", user.getUser().getFullName());
        model.addAttribute("email", user.getUser().getEmail());
        model.addAttribute("role", user.getAuthorities().toString());
        return "admin/profile";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal LibraryUserDetails user,
                       @RequestParam String username,
                       RedirectAttributes ra) {
        if (username == null || username.trim().isEmpty()) {
            ra.addFlashAttribute("error", "Username is required.");
            return "redirect:/admin/profile";
        }
        String trimmedUsername = username.trim();
        if (!trimmedUsername.matches("^[A-Za-z0-9_-]+$")) {
            ra.addFlashAttribute("error", "Username can only contain letters, numbers, underscore, and hyphen. No spaces or special characters allowed.");
            return "redirect:/admin/profile";
        }
        String currentUsername = user.getUsername();
        if (!trimmedUsername.equals(currentUsername)) {
            if (userRepository.existsByUsername(trimmedUsername)) {
                ra.addFlashAttribute("error", "Username '" + trimmedUsername + "' is already taken.");
                return "redirect:/admin/profile";
            }
        }

        User currentUser = userRepository.findByUsername(currentUsername).orElseThrow();
        currentUser.setUsername(trimmedUsername);
        userRepository.save(currentUser);

        ra.addFlashAttribute("success", "Username updated successfully.");
        return "redirect:/admin/profile";
    }
}
