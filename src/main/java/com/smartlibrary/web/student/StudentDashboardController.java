package com.smartlibrary.web.student;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
public class StudentDashboardController {

    private final StudentProfileRepository studentProfileRepository;
    private final BookIssueService bookIssueService;
    private final BookService bookService;
    private final ReservationService reservationService;
    private final LibraryProperties libraryProperties;

    public StudentDashboardController(
            StudentProfileRepository studentProfileRepository,
            BookIssueService bookIssueService,
            BookService bookService,
            ReservationService reservationService,
            LibraryProperties libraryProperties) {
        this.studentProfileRepository = studentProfileRepository;
        this.bookIssueService = bookIssueService;
        this.bookService = bookService;
        this.reservationService = reservationService;
        this.libraryProperties = libraryProperties;
    }

    @Transactional(readOnly = true)
    @GetMapping({"", "/"})
    public String dashboard(@AuthenticationPrincipal LibraryUserDetails user, Model model) {
        var profileOpt = studentProfileRepository.findByUserUsername(user.getUsername());
        if (profileOpt.isEmpty()) {
            model.addAttribute("error", "Student profile not found. Please contact administrator.");
            return "error";
        }
        var profile = profileOpt.get();
        var issues = bookIssueService.issuesForStudent(profile.getId());
        
        long borrowedCount = issues.stream().filter(i -> i.getStatus().toString().equals("BORROWED")).count();
        long overdueCount = issues.stream().filter(i -> i.getStatus().toString().equals("OVERDUE")).count();
        
        model.addAttribute("profile", profile);
        model.addAttribute("issues", issues);
        model.addAttribute("borrowedCount", borrowedCount);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("reservations", reservationService.forStudent(profile.getId()));
        model.addAttribute("digitalBooks", bookService.listDigitalBooksForStudent(profile.getId()));
        model.addAttribute("defaultFinePerDay", libraryProperties.getFinePerDay());
        return "student/dashboard";
    }

    @Transactional
    @PostMapping("/issues/{id}/return")
    public String returnBook(
            @AuthenticationPrincipal LibraryUserDetails user,
            @PathVariable("id") Long issueId,
            RedirectAttributes ra) {
        try {
            var profileOpt = studentProfileRepository.findByUserUsername(user.getUsername());
            if (profileOpt.isEmpty()) {
                ra.addFlashAttribute("error", "Student profile not found.");
                return "redirect:/student";
            }
            var profile = profileOpt.get();
            var issue = bookIssueService.findById(issueId).orElseThrow(() -> new IllegalArgumentException("Issue not found"));
            if (!issue.getStudent().getId().equals(profile.getId())) {
                ra.addFlashAttribute("error", "You are not allowed to return this issue record.");
                return "redirect:/student";
            }

            var returned = bookIssueService.returnBook(issueId);
            ra.addFlashAttribute("success", "Book returned successfully. Fine: " + returned.getFineAmount());
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student";
    }

    @Transactional
    @PostMapping("/issues/{id}/delete-history")
    public String deleteHistory(
            @AuthenticationPrincipal LibraryUserDetails user,
            @PathVariable("id") Long issueId,
            RedirectAttributes ra) {
        try {
            var profileOpt = studentProfileRepository.findByUserUsername(user.getUsername());
            if (profileOpt.isEmpty()) {
                ra.addFlashAttribute("error", "Student profile not found.");
                return "redirect:/student";
            }
            bookIssueService.deleteReturnedHistoryForStudent(issueId, profileOpt.get().getId());
            ra.addFlashAttribute("success", "Returned history record deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student";
    }
}
