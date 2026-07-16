package com.smartlibrary.web.student;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.ProfilePhotoService;
import com.smartlibrary.service.SharedLibraryStudentProfileBridgeService;
import com.smartlibrary.service.UserAccountService;
import com.smartlibrary.web.SafeRedirects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student/profile")
public class StudentProfileController {

    private final UserAccountService userAccountService;
    private final SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService;
    private final ProfilePhotoService profilePhotoService;

    public StudentProfileController(
            UserAccountService userAccountService,
            SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService,
            ProfilePhotoService profilePhotoService) {
        this.userAccountService = userAccountService;
        this.sharedLibraryStudentProfileBridgeService = sharedLibraryStudentProfileBridgeService;
        this.profilePhotoService = profilePhotoService;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal LibraryUserDetails user, Model model) {
        var profile = sharedLibraryStudentProfileBridgeService.ensureLibraryStudentProfile(user.getUser()).orElseThrow();
        model.addAttribute("profile", profile);
        return "student/profile";
    }

    @PostMapping
    public String save(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String course,
            RedirectAttributes ra) {
        try {
            userAccountService.updateStudentProfile(user.getUsername(), fullName, phone, course);
            ra.addFlashAttribute("success", "Profile updated successfully");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/profile";
    }

    @PostMapping("/photo")
    public String uploadPhoto(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam("photo") MultipartFile photo,
            RedirectAttributes ra,
            HttpServletRequest request) {
        try {
            profilePhotoService.saveProfilePhoto(user.getUsername(), photo);
            ra.addFlashAttribute("success", "Profile photo updated successfully.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Unable to upload profile photo.");
        }
        return SafeRedirects.toRefererOr(request, "/student/profile");
    }
}
