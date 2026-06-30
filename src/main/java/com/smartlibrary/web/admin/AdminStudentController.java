package com.smartlibrary.web.admin;

import com.smartlibrary.repository.StudentProfileRepository;
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

    private final StudentProfileRepository studentProfileRepository;
    private final BookIssueService bookIssueService;
    private final MailNotificationService mailNotificationService;

    public AdminStudentController(
            StudentProfileRepository studentProfileRepository,
            BookIssueService bookIssueService,
            MailNotificationService mailNotificationService) {
        this.studentProfileRepository = studentProfileRepository;
        this.bookIssueService = bookIssueService;
        this.mailNotificationService = mailNotificationService;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public String search(@RequestParam(required = false) String query, Model model) {
        try {
            if (query != null && !query.isBlank()) {
                var results = studentProfileRepository.searchStudents(query.trim());
                model.addAttribute("students", results);
                model.addAttribute("query", query);
                model.addAttribute("isSearch", true);
                if (results.isEmpty()) {
                    model.addAttribute("notFound", true);
                }
            } else {
                var allStudents = studentProfileRepository.findAllWithUsers();
                model.addAttribute("students", allStudents);
                model.addAttribute("isSearch", false);
                if (allStudents.isEmpty()) {
                    model.addAttribute("noStudents", true);
                }
            }
        } catch (Exception e) {
            model.addAttribute("students", List.of());
            model.addAttribute("isSearch", false);
            model.addAttribute("noStudents", true);
            model.addAttribute("error", "Unable to load students: " + e.getMessage());
        }
        return "admin/student-lookup";
    }

    @Transactional(readOnly = true)
    @GetMapping("/{studentId}/history")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> history(@PathVariable String studentId) {
        var profileOpt = studentProfileRepository.findByStudentId(studentId);
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
}
