package com.smartlibrary.config;

import com.smartlibrary.service.ProfilePhotoService;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ProfileModelAdvice {

    private final ProfilePhotoService profilePhotoService;

    public ProfileModelAdvice(ProfilePhotoService profilePhotoService) {
        this.profilePhotoService = profilePhotoService;
    }

    @ModelAttribute
    public void addProfileAttributes(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            model.addAttribute("profilePhotoUrl", null);
            model.addAttribute("profileInitial", "U");
            return;
        }

        String username = authentication.getName();
        model.addAttribute("profilePhotoUrl", profilePhotoService.resolveProfilePhotoUrl(username));
        model.addAttribute("profileInitial", profilePhotoService.getInitial(username));
    }
}
