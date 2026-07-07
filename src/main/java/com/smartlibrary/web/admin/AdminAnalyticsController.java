package com.smartlibrary.web.admin;

import com.smartlibrary.service.AdminStudentManagementService;
import com.smartlibrary.service.AnalyticsService;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.MailNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;
    private final AdminStudentManagementService adminStudentManagementService;
    private final BookIssueService bookIssueService;
    private final MailNotificationService mailNotificationService;

    public AdminAnalyticsController(
            AnalyticsService analyticsService,
            AdminStudentManagementService adminStudentManagementService,
            BookIssueService bookIssueService,
            MailNotificationService mailNotificationService) {
        this.analyticsService = analyticsService;
        this.adminStudentManagementService = adminStudentManagementService;
        this.bookIssueService = bookIssueService;
        this.mailNotificationService = mailNotificationService;
    }

    @GetMapping
    public String analytics(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "studentId", required = false) String studentId,
            Model model) {
        Map<String, Long> statusCounts = analyticsService.issueStatusCounts();
        model.addAttribute("topBooks", analyticsService.mostBorrowedBooks(10));
        model.addAttribute("overdueRate", analyticsService.overdueRatePercent());
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("totalIssues", analyticsService.totalIssues());
        model.addAttribute("borrowsByDayOfWeek", analyticsService.borrowsByDayOfWeek());
        model.addAttribute("borrowsByRecentDays", analyticsService.borrowsByRecentDays(14));
        analyticsService.busiestDayOfWeek().ifPresent(day -> model.addAttribute("busiestDayOfWeek", day));
        analyticsService.busiestRecentDay(14).ifPresent(day -> model.addAttribute("busiestRecentDay", day));

        model.addAttribute("query", query != null ? query : "");
        model.addAttribute("selectedStudentId", studentId);
        model.addAttribute("selectedStudent", null);
        model.addAttribute("readingHistory", List.of());
        model.addAttribute("searchResults", List.of());

        String resolvedStudentId = studentId;
        if ((resolvedStudentId == null || resolvedStudentId.isBlank()) && query != null && !query.isBlank()) {
            var results = adminStudentManagementService.listStudents(query.trim(), false);
            if (results.isEmpty()) {
                results = adminStudentManagementService.listStudents(query.trim(), true);
            }
            model.addAttribute("searchResults", results);
            if (results.size() == 1) {
                resolvedStudentId = results.get(0).getStudentId();
            } else {
                var exact = results.stream()
                        .filter(s -> s.getStudentId().equalsIgnoreCase(query.trim()))
                        .findFirst();
                if (exact.isPresent()) {
                    resolvedStudentId = exact.get().getStudentId();
                }
            }
        }

        if (resolvedStudentId != null && !resolvedStudentId.isBlank()) {
            adminStudentManagementService.findByStudentId(resolvedStudentId).ifPresent(profile -> {
                model.addAttribute("selectedStudent", profile);
                model.addAttribute("selectedStudentId", profile.getStudentId());
                model.addAttribute("readingHistory", analyticsService.studentReadingHistory(profile.getId()));
            });
        }
        return "admin/analytics";
    }

    @PostMapping("/issues/{issueId}/remind")
    public String remindStudentFromAnalytics(
            @PathVariable Long issueId,
            @RequestParam(required = false) String studentId,
            RedirectAttributes ra) {
        try {
            var issue = bookIssueService.findByIdWithBookStudentUser(issueId)
                    .orElseThrow(() -> new IllegalArgumentException("Issue record not found"));
            boolean sent = mailNotificationService.sendDueReminder(issue);
            if (sent) {
                ra.addFlashAttribute("success", "Reminder sent to " + issue.getStudent().getFullName());
            } else {
                ra.addFlashAttribute("error", "Reminder failed. Check Gmail SMTP credentials/app password.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (studentId != null && !studentId.isBlank()) {
            return "redirect:/admin/analytics?studentId=" + studentId;
        }
        return "redirect:/admin/analytics";
    }
}
