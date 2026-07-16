package com.smartlibrary.web.admin;

import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.ProfilePhotoService;
import com.smartlibrary.web.SafeRedirects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/profile")
public class AdminProfileController {

    private final UserRepository userRepository;
    private final ProfilePhotoService profilePhotoService;

    public AdminProfileController(UserRepository userRepository, ProfilePhotoService profilePhotoService) {
        this.userRepository = userRepository;
        this.profilePhotoService = profilePhotoService;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal LibraryUserDetails user, Model model) {
        User fresh = userRepository.findById(user.getUser().getId()).orElseThrow();
        boolean superAdmin = fresh.getRole() == UserRole.SUPER_ADMIN;

        String displayName = (fresh.getFullName() != null && !fresh.getFullName().isBlank())
                ? fresh.getFullName()
                : fresh.getUsername();

        Map<String, String> profileDetails = new LinkedHashMap<>();
        profileDetails.put("Username", fresh.getUsername());
        profileDetails.put("Email", fresh.getEmail() != null ? fresh.getEmail() : "—");
        profileDetails.put("Role", superAdmin ? "Super Admin" : "Administrator");
        profileDetails.put("Account Status", fresh.isEnabled() ? "Active" : "Disabled");
        profileDetails.put("Created At", fresh.getCreatedAt() != null
                ? fresh.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : "—");

        model.addAttribute("username", fresh.getUsername());
        model.addAttribute("displayName", displayName);
        model.addAttribute("profileCode", "ID: " + fresh.getId());
        model.addAttribute("roleLabel", superAdmin ? "Super Admin" : "Administrator");
        model.addAttribute("accountActive", fresh.isEnabled());
        model.addAttribute("memberSince", fresh.getCreatedAt() != null
                ? fresh.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                : "—");
        model.addAttribute("profileDetails", profileDetails);
        model.addAttribute("dashboardPath", superAdmin ? "/super-admin" : "/admin");
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

        User currentUser = userRepository.findById(user.getUser().getId()).orElseThrow();
        if (!trimmedUsername.equals(currentUser.getUsername())
                && userRepository.existsByUsername(trimmedUsername)) {
            ra.addFlashAttribute("error", "Username '" + trimmedUsername + "' is already taken.");
            return "redirect:/admin/profile";
        }

        currentUser.setUsername(trimmedUsername);
        User saved = userRepository.save(currentUser);

        LibraryUserDetails updatedDetails = new LibraryUserDetails(saved);
        var authentication = new UsernamePasswordAuthenticationToken(
                updatedDetails,
                updatedDetails.getPassword(),
                updatedDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ra.addFlashAttribute("success", "Username updated to \"" + trimmedUsername + "\".");
        return "redirect:/admin/profile";
    }

    @PostMapping("/photo")
    public String uploadPhoto(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam("photo") MultipartFile photo,
            RedirectAttributes ra,
            HttpServletRequest request) {
        try {
            User fresh = userRepository.findById(user.getUser().getId()).orElseThrow();
            profilePhotoService.saveProfilePhoto(fresh.getUsername(), photo);
            ra.addFlashAttribute("success", "Profile photo updated successfully.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Unable to upload profile photo.");
        }
        return SafeRedirects.toRefererOr(request, "/admin/profile");
    }
}
