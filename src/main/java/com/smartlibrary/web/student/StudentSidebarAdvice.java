package com.smartlibrary.web.student;

import com.smartlibrary.model.UserRole;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.SharedLibraryStudentProfileBridgeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class StudentSidebarAdvice {

    private final BookIssueService bookIssueService;
    private final SharedLibraryStudentProfileBridgeService profileBridgeService;

    public StudentSidebarAdvice(
            BookIssueService bookIssueService,
            SharedLibraryStudentProfileBridgeService profileBridgeService) {
        this.bookIssueService = bookIssueService;
        this.profileBridgeService = profileBridgeService;
    }

    @ModelAttribute
    public void addStudentSidebarStats(@AuthenticationPrincipal LibraryUserDetails user,
                                       org.springframework.ui.Model model) {
        if (user == null || user.getUser().getRole() != UserRole.STUDENT) {
            return;
        }
        if (model.containsAttribute("studentBorrowedCount")) {
            return;
        }
        profileBridgeService.ensureLibraryStudentProfile(user.getUser()).ifPresent(profile -> {
            var issues = bookIssueService.issuesForStudent(profile.getId());
            long borrowed = issues.stream().filter(i -> "BORROWED".equals(i.getStatus().name())).count();
            long overdue = issues.stream().filter(i -> "OVERDUE".equals(i.getStatus().name())).count();
            long returned = issues.stream().filter(i -> "RETURNED".equals(i.getStatus().name())).count();
            model.addAttribute("studentBorrowedCount", borrowed);
            model.addAttribute("studentOverdueCount", overdue);
            model.addAttribute("studentReturnedCount", returned);
        });
    }
}
