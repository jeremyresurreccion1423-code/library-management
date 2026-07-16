package com.smartlibrary.web.superadmin.library;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/superadmin/library")
public class SuperAdminLibraryScanIssueController {

    @GetMapping("/scan-issue")
    public String page() {
        return "superadmin/library/scan-issue";
    }
}
