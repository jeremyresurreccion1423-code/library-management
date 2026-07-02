package com.smartlibrary.web.student;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.SharedLibraryStudentProfileBridgeService;
import com.smartlibrary.service.UserAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student/profile")
public class StudentProfileController {

    private final UserAccountService userAccountService;
    private final SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService;

    public StudentProfileController(
            UserAccountService userAccountService,
            SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService) {
        this.userAccountService = userAccountService;
        this.sharedLibraryStudentProfileBridgeService = sharedLibraryStudentProfileBridgeService;
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
}
