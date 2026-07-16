package com.smartlibrary.web.admin;

import com.smartlibrary.entity.User;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.model.ReservationStatus;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.ReservationRepository;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.ProfilePhotoService;
import com.smartlibrary.web.SafeRedirects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/profile")
public class AdminProfileController {

    private static final DateTimeFormatter ACTIVITY_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final UserRepository userRepository;
    private final ProfilePhotoService profilePhotoService;
    private final BookRepository bookRepository;
    private final BookIssueRepository bookIssueRepository;
    private final ReservationRepository reservationRepository;

    public AdminProfileController(
            UserRepository userRepository,
            ProfilePhotoService profilePhotoService,
            BookRepository bookRepository,
            BookIssueRepository bookIssueRepository,
            ReservationRepository reservationRepository) {
        this.userRepository = userRepository;
        this.profilePhotoService = profilePhotoService;
        this.bookRepository = bookRepository;
        this.bookIssueRepository = bookIssueRepository;
        this.reservationRepository = reservationRepository;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal LibraryUserDetails user, Model model) {
        User fresh = userRepository.findById(user.getUser().getId()).orElseThrow();

        String displayName = (fresh.getFullName() != null && !fresh.getFullName().isBlank())
                ? fresh.getFullName()
                : fresh.getUsername();

        Map<String, String> profileDetails = new LinkedHashMap<>();
        profileDetails.put("Username", fresh.getUsername());
        profileDetails.put("Full Name", fresh.getFullName() != null && !fresh.getFullName().isBlank()
                ? fresh.getFullName() : "—");
        profileDetails.put("Email", fresh.getEmail() != null ? fresh.getEmail() : "—");
        profileDetails.put("Role", fresh.getRole().name().toLowerCase().replace('_', ' '));
        profileDetails.put("Account", fresh.isEnabled() ? "Active" : "Disabled");
        profileDetails.put("Created At", fresh.getCreatedAt() != null
                ? fresh.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : "—");

        boolean superAdmin = fresh.getRole() == UserRole.SUPER_ADMIN;
        model.addAttribute("username", fresh.getUsername());
        model.addAttribute("fullName", fresh.getFullName());
        model.addAttribute("email", fresh.getEmail());
        model.addAttribute("displayName", displayName);
        model.addAttribute("profileCode", "ID " + fresh.getId());
        model.addAttribute("roleName", fresh.getRole().name());
        model.addAttribute("roleLabel", superAdmin ? "Super Admin" : "Administrator");
        model.addAttribute("logoutMode", superAdmin ? "super-admin" : "admin");
        model.addAttribute("profileDetails", profileDetails);
        model.addAttribute("accountActive", fresh.isEnabled());
        model.addAttribute("dashboardPath", superAdmin ? "/super-admin" : "/admin");
        model.addAttribute("statBooks", bookRepository.count());
        model.addAttribute("statActiveLoans", bookIssueRepository.countByStatus(IssueStatus.BORROWED));
        model.addAttribute("statOverdue", bookIssueRepository.countByStatus(IssueStatus.OVERDUE));
        model.addAttribute("statReservations", reservationRepository.countByStatus(ReservationStatus.WAITING));
        model.addAttribute("recentActivities", buildRecentActivities());
        return "admin/profile";
    }

    private List<Map<String, String>> buildRecentActivities() {
        List<Map<String, String>> activities = new ArrayList<>();
        var recent = bookIssueRepository.findRecentWithDetails(PageRequest.of(0, 5));
        for (var issue : recent) {
            String type;
            String icon;
            String tone;
            LocalDateTime when = issue.getIssuedAt();

            if (issue.getStatus() == IssueStatus.RETURNED && issue.getReturnedAt() != null) {
                type = "Book returned";
                icon = "bi-arrow-return-left";
                tone = "blue";
                when = issue.getReturnedAt();
            } else if (issue.getStatus() == IssueStatus.OVERDUE) {
                type = "Overdue book";
                icon = "bi-exclamation-circle";
                tone = "red";
            } else {
                type = "Book issued";
                icon = "bi-journal-arrow-up";
                tone = "green";
            }

            String studentName = issue.getStudent().getFullName() != null
                    ? issue.getStudent().getFullName()
                    : issue.getStudent().getUser().getUsername();

            activities.add(Map.of(
                    "type", type,
                    "icon", icon,
                    "tone", tone,
                    "title", issue.getBook().getTitle(),
                    "subtitle", studentName,
                    "when", formatActivityTime(when)));
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

    @PostMapping
    public String save(@AuthenticationPrincipal LibraryUserDetails user,
                       @RequestParam String username,
                       RedirectAttributes ra) {
        if (username == null || username.trim().isEmpty()) {
            ra.addFlashAttribute("error", "Username is required.");
            return "redirect:/admin/profile";
        }
        String trimmedUsername = username.trim();
        if (!trimmedUsername.matches("^[A-Za-z0-9_-]+$")) {
            ra.addFlashAttribute("error", "Username can only contain letters, numbers, underscore, and hyphen. No spaces or special characters allowed.");
            return "redirect:/admin/profile";
        }

        User currentUser = userRepository.findById(user.getUser().getId()).orElseThrow();
        if (!trimmedUsername.equals(currentUser.getUsername())
                && userRepository.existsByUsername(trimmedUsername)) {
            ra.addFlashAttribute("error", "Username '" + trimmedUsername + "' is already taken.");
            return "redirect:/admin/profile";
        }

        currentUser.setUsername(trimmedUsername);
        User saved = userRepository.save(currentUser);

        LibraryUserDetails updatedDetails = new LibraryUserDetails(saved);
        var authentication = new UsernamePasswordAuthenticationToken(
                updatedDetails,
                updatedDetails.getPassword(),
                updatedDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ra.addFlashAttribute("success", "Username updated to \"" + trimmedUsername + "\".");
        return "redirect:/admin/profile";
    }

    @PostMapping("/photo")
    public String uploadPhoto(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam("photo") MultipartFile photo,
            RedirectAttributes ra,
            HttpServletRequest request) {
        try {
            User fresh = userRepository.findById(user.getUser().getId()).orElseThrow();
            profilePhotoService.saveProfilePhoto(fresh.getUsername(), photo);
            ra.addFlashAttribute("success", "Profile photo updated successfully.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Unable to upload profile photo.");
        }
        return SafeRedirects.toRefererOr(request, "/admin/profile");
    }
}
