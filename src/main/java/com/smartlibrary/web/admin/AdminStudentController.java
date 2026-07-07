package com.smartlibrary.web.admin;

import com.smartlibrary.service.AdminStudentManagementService;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.MailNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/students")
public class AdminStudentController {

    private final AdminStudentManagementService adminStudentManagementService;
    private final BookIssueService bookIssueService;
    private final MailNotificationService mailNotificationService;

    public AdminStudentController(
            AdminStudentManagementService adminStudentManagementService,
            BookIssueService bookIssueService,
            MailNotificationService mailNotificationService) {
        this.adminStudentManagementService = adminStudentManagementService;
        this.bookIssueService = bookIssueService;
        this.mailNotificationService = mailNotificationService;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public String search(@RequestParam(required = false) String query, Model model) {
        try {
            var students = adminStudentManagementService.listStudents(query);
            model.addAttribute("students", students);
            model.addAttribute("query", query != null ? query : "");
            model.addAttribute("isSearch", query != null && !query.isBlank());
            if (query != null && !query.isBlank() && students.isEmpty()) {
                model.addAttribute("notFound", true);
            }
            if ((query == null || query.isBlank()) && students.isEmpty()) {
                model.addAttribute("noStudents", true);
            }
        } catch (Exception e) {
            model.addAttribute("students", List.of());
            model.addAttribute("query", query != null ? query : "");
            model.addAttribute("isSearch", false);
            model.addAttribute("error", "Unable to load students: " + e.getMessage());
        }
        return "admin/student-lookup";
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id,
                          @RequestParam(required = false) String query,
                          RedirectAttributes ra) {
        try {
            adminStudentManagementService.archive(id);
            ra.addFlashAttribute("success", "Student archived. Records are kept and login is disabled.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToList(query);
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id,
                          @RequestParam(required = false) String query,
                          RedirectAttributes ra) {
        try {
            adminStudentManagementService.restore(id);
            ra.addFlashAttribute("success", "Student restored to active.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToList(query);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String query,
                         RedirectAttributes ra) {
        try {
            adminStudentManagementService.delete(id);
            ra.addFlashAttribute("success", "Student removed from the library system.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToList(query);
    }

    @Transactional(readOnly = true)
    @GetMapping("/{studentId}/history")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> history(@PathVariable String studentId) {
        var profileOpt = adminStudentManagementService.findByStudentId(studentId);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var issues = bookIssueService.issuesForStudent(profileOpt.get().getId());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        List<Map<String, Object>> rows = issues.stream().map(issue -> {
            Map<String, Object> row = new HashMap<>();
            row.put("issueId", issue.getId());
            row.put("bookTitle", issue.getBook().getTitle());
            row.put("issuedAt", issue.getIssuedAt() != null ? issue.getIssuedAt().format(dtf) : "-");
            row.put("dueAt", issue.getDueAt() != null ? issue.getDueAt().format(dtf) : "-");
            row.put("returnedAt", issue.getReturnedAt() != null ? issue.getReturnedAt().format(dtf) : "-");
            row.put("fineAmount", issue.getFineAmount() != null ? issue.getFineAmount().toPlainString() : "0.00");
            row.put("status", issue.getStatus().name());
            return row;
        }).toList();

        return ResponseEntity.ok(Map.of("history", rows));
    }

    @PostMapping("/issues/{issueId}/remind")
    public String remindStudent(@PathVariable Long issueId, RedirectAttributes ra) {
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
        return "redirect:/admin/students";
    }

    private String redirectToList(String query) {
        if (query != null && !query.isBlank()) {
            return "redirect:/admin/students?query="
                    + java.net.URLEncoder.encode(query.trim(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return "redirect:/admin/students";
    }
}
