package com.smartlibrary.web.student;

import com.smartlibrary.entity.BookIssue;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.model.ReservationStatus;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.ProfilePhotoService;
import com.smartlibrary.service.ReservationService;
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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/student/profile")
public class StudentProfileController {

    private static final DateTimeFormatter ACTIVITY_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final UserAccountService userAccountService;
    private final SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService;
    private final ProfilePhotoService profilePhotoService;
    private final BookIssueService bookIssueService;
    private final ReservationService reservationService;
    private final BookService bookService;

    public StudentProfileController(
            UserAccountService userAccountService,
            SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService,
            ProfilePhotoService profilePhotoService,
            BookIssueService bookIssueService,
            ReservationService reservationService,
            BookService bookService) {
        this.userAccountService = userAccountService;
        this.sharedLibraryStudentProfileBridgeService = sharedLibraryStudentProfileBridgeService;
        this.profilePhotoService = profilePhotoService;
        this.bookIssueService = bookIssueService;
        this.reservationService = reservationService;
        this.bookService = bookService;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal LibraryUserDetails user, Model model) {
        var profile = sharedLibraryStudentProfileBridgeService.ensureLibraryStudentProfile(user.getUser()).orElseThrow();
        var issues = bookIssueService.issuesForStudent(profile.getId());

        model.addAttribute("profile", profile);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userEmail", user.getUser().getEmail());
        model.addAttribute("accountActive", user.getUser().isEnabled());
        model.addAttribute("statBorrowed", issues.stream().filter(i -> i.getStatus() == IssueStatus.BORROWED).count());
        model.addAttribute("statOverdue", issues.stream().filter(i -> i.getStatus() == IssueStatus.OVERDUE).count());
        model.addAttribute("statReservations", reservationService.forStudent(profile.getId()).stream()
                .filter(r -> r.getStatus() == ReservationStatus.WAITING).count());
        model.addAttribute("statDigital", bookService.listDigitalBooksForStudent(profile.getId()).size());
        model.addAttribute("recentActivities", buildStudentActivities(issues));
        return "student/profile";
    }

    private List<Map<String, String>> buildStudentActivities(List<BookIssue> issues) {
        List<Map<String, String>> activities = new ArrayList<>();
        for (BookIssue issue : issues.stream().limit(5).toList()) {
            String type;
            String icon;
            String tone;
            if (issue.getStatus() == IssueStatus.RETURNED) {
                type = "Returned";
                icon = "bi-arrow-return-left";
                tone = "blue";
            } else if (issue.getStatus() == IssueStatus.OVERDUE) {
                type = "Overdue";
                icon = "bi-exclamation-circle";
                tone = "red";
            } else {
                type = "Borrowed";
                icon = "bi-journal-arrow-up";
                tone = "green";
            }
            var when = issue.getReturnedAt() != null ? issue.getReturnedAt() : issue.getIssuedAt();
            activities.add(Map.of(
                    "type", type,
                    "icon", icon,
                    "tone", tone,
                    "title", issue.getBook().getTitle(),
                    "when", when != null ? when.format(ACTIVITY_TIME) : "—"));
        }
        return activities;
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
