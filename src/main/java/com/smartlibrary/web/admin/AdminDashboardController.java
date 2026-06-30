package com.smartlibrary.web.admin;

import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.UserAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final BookRepository bookRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final BookIssueRepository bookIssueRepository;
    private final UserAccountService userAccountService;
    private final UserRepository userRepository;

    public AdminDashboardController(
            BookRepository bookRepository,
            StudentProfileRepository studentProfileRepository,
            BookIssueRepository bookIssueRepository,
            UserAccountService userAccountService,
            UserRepository userRepository) {
        this.bookRepository = bookRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.bookIssueRepository = bookIssueRepository;
        this.userAccountService = userAccountService;
        this.userRepository = userRepository;
    }

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("bookCount", bookRepository.count());
        model.addAttribute("studentCount", studentProfileRepository.count());
        model.addAttribute("activeLoans", bookIssueRepository.countByStatus(com.smartlibrary.model.IssueStatus.BORROWED));
        model.addAttribute("overdueLoans", bookIssueRepository.countByStatus(com.smartlibrary.model.IssueStatus.OVERDUE));
        return "admin/dashboard";
    }

    @GetMapping("/admins")
    public String listAdmins(Model model, @AuthenticationPrincipal LibraryUserDetails currentUser) {
        model.addAttribute("admins", userRepository.findByRoleOrderByUsernameAsc(UserRole.ADMIN));
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("adminCount", userRepository.countByRole(UserRole.ADMIN));
        return "admin/admins";
    }

    @GetMapping("/admins/new")
    public String newAdminForm(Model model) {
        return "admin/admin-form";
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
            return "redirect:/admin/admins";
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/admins/new";
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
        return "redirect:/admin/admins";
    }
}
