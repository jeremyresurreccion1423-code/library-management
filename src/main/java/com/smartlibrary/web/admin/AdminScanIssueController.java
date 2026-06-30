package com.smartlibrary.web.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminScanIssueController {

    @GetMapping("/scan-issue")
    public String page() {
        return "admin/scan-issue";
    }
}

