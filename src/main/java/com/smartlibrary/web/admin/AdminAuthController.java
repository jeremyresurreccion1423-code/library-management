package com.smartlibrary.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminAuthController {

    @GetMapping("/login")
    public String loginGet(@RequestParam(required = false) String logout,
                           @RequestParam(required = false) String error) {
        String target = "/login";
        if (logout != null) {
            target += "?logout=true";
        } else if (error != null) {
            target += "?error=true";
        }
        return "redirect:" + target;
    }

    @PostMapping({"/login", "/login/process"})
    public String loginPost() {
        return "redirect:/login";
    }
}
