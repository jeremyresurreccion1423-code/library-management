package com.smartlibrary.web.superadmin.library;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.model.ReservationStatus;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.ReservationRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.AnalyticsService;
import com.smartlibrary.service.UserAccountService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/superadmin/library")
public class SuperAdminLibraryDashboardController {

    private static final DateTimeFormatter ACTIVITY_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final BookRepository bookRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final BookIssueRepository bookIssueRepository;
    private final ReservationRepository reservationRepository;
    private final UserAccountService userAccountService;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    public SuperAdminLibraryDashboardController(
            BookRepository bookRepository,
            StudentProfileRepository studentProfileRepository,
            BookIssueRepository bookIssueRepository,
            ReservationRepository reservationRepository,
            UserAccountService userAccountService,
            UserRepository userRepository,
            AnalyticsService analyticsService,
            ObjectMapper objectMapper) {
        this.bookRepository = bookRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.bookIssueRepository = bookIssueRepository;
        this.reservationRepository = reservationRepository;
        this.userAccountService = userAccountService;
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
        this.objectMapper = objectMapper;
    }

    @GetMapping({"/dashboard", ""})
    public String dashboard(@AuthenticationPrincipal LibraryUserDetails user, Model model)
            throws JsonProcessingException {
        model.addAttribute("bookCount", bookRepository.count());
        model.addAttribute("studentCount", studentProfileRepository.count());
        model.addAttribute("activeLoans", bookIssueRepository.countByStatus(IssueStatus.BORROWED));
        model.addAttribute("reservationCount", reservationRepository.countByStatus(ReservationStatus.WAITING));
        model.addAttribute("overdueLoans", bookIssueRepository.countByStatus(IssueStatus.OVERDUE));
        model.addAttribute("topBooks", analyticsService.mostBorrowedBooks(5));
        model.addAttribute("overdueBooks", bookIssueRepository.findByStatusWithDetails(IssueStatus.OVERDUE));
        model.addAttribute("recentActivities", buildRecentActivities());

        List<Map<String, Object>> trend = analyticsService.borrowsByRecentDays(14);
        model.addAttribute("dailyTrendJson", objectMapper.writeValueAsString(trend));

        String displayName = user != null && user.getUser().getFullName() != null
                && !user.getUser().getFullName().isBlank()
                ? user.getUser().getFullName()
                : user != null ? user.getUsername() : "Super Admin";
        model.addAttribute("adminDisplayName", displayName);
        return "superadmin/library/dashboard";
    }

    @GetMapping("/admins")
    public String listAdmins(Model model, @AuthenticationPrincipal LibraryUserDetails currentUser) {
        model.addAttribute("admins", userRepository.findByRoleOrderByUsernameAsc(UserRole.ADMIN));
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("adminCount", userRepository.countByRole(UserRole.ADMIN));
        return "superadmin/library/admins";
    }

    @GetMapping("/admins/new")
    public String newAdminForm() {
        return "superadmin/library/admin-form";
    }

    @PostMapping("/admins/new")
    public String createAdmin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes ra) {
        try {
            if (!password.equals(confirmPassword)) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            userAccountService.registerAdmin(username, password);
            ra.addFlashAttribute("success", "Admin account created successfully");
            return "redirect:/superadmin/library/admins";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/library/admins/new";
        }
    }

    @PostMapping("/admins/{id}/delete")
    public String deleteAdmin(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal LibraryUserDetails currentUser,
            RedirectAttributes ra) {
        try {
            var target = userRepository.findById(id).orElseThrow(() ->
                    new IllegalArgumentException("Admin not found"));
            if (target.getRole() != UserRole.ADMIN) {
                throw new IllegalArgumentException("Not an admin account");
            }
            if (target.getUsername().equals(currentUser.getUsername())) {
                throw new IllegalArgumentException("You cannot delete your own account");
            }
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last admin account");
            }
            userRepository.deleteById(id);
            ra.addFlashAttribute("success", "Admin account deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/superadmin/library/admins";
    }

    private List<Map<String, Object>> buildRecentActivities() {
        List<Map<String, Object>> activities = new ArrayList<>();
        var recent = bookIssueRepository.findRecentWithDetails(PageRequest.of(0, 8));
        for (var issue : recent) {
            String studentName = issue.getStudent().getFullName() != null
                    ? issue.getStudent().getFullName()
                    : issue.getStudent().getUser().getUsername();
            String type;
            String tone;
            LocalDateTime when = issue.getIssuedAt();

            if (issue.getStatus() == IssueStatus.RETURNED && issue.getReturnedAt() != null) {
                type = "Book returned";
                tone = "blue";
                when = issue.getReturnedAt();
            } else if (issue.getStatus() == IssueStatus.OVERDUE) {
                type = "Overdue book";
                tone = "red";
            } else {
                type = "Book issued";
                tone = "green";
            }

            activities.add(Map.of(
                    "type", type,
                    "tone", tone,
                    "bookTitle", issue.getBook().getTitle(),
                    "personName", studentName,
                    "whenLabel", formatActivityTime(when)));
        }
        return activities;
    }

    private static String formatActivityTime(LocalDateTime when) {
        if (when == null) {
            return "";
        }
        long days = ChronoUnit.DAYS.between(when.toLocalDate(), LocalDateTime.now().toLocalDate());
        if (days == 0) {
            return "Today, " + when.format(DateTimeFormatter.ofPattern("h:mm a"));
        }
        if (days == 1) {
            return "Yesterday, " + when.format(DateTimeFormatter.ofPattern("h:mm a"));
        }
        return when.format(ACTIVITY_TIME);
    }
}
