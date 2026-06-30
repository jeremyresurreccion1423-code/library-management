package com.smartlibrary.web.student;

import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/student")
public class StudentReservationController {

    private final StudentProfileRepository studentProfileRepository;
    private final ReservationService reservationService;
    private final BookIssueService bookIssueService;

    public StudentReservationController(
            StudentProfileRepository studentProfileRepository,
            ReservationService reservationService,
            BookIssueService bookIssueService) {
        this.studentProfileRepository = studentProfileRepository;
        this.reservationService = reservationService;
        this.bookIssueService = bookIssueService;
    }

    @PostMapping("/borrow")
    public String borrow(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam Long bookId,
            RedirectAttributes ra) {
        try {
            var profile = studentProfileRepository.findByUserUsername(user.getUsername())
                    .orElseThrow(() -> new IllegalStateException("Student profile not found for this account. Please login as a student user."));
            var issue = bookIssueService.issueToStudent(bookId, profile.getStudentId());
            ra.addFlashAttribute("success", "Book borrowed successfully! Due: " + issue.getDueAt());
        } catch (Exception e) {
            String message = (e.getMessage() == null || e.getMessage().isBlank())
                    ? "Borrow failed. Please try again or contact admin."
                    : e.getMessage();
            ra.addFlashAttribute("error", message);
        }
        return "redirect:/books/" + bookId;
    }

    @PostMapping("/reservations/add")
    public String add(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam Long bookId,
            RedirectAttributes ra) {
        try {
            var profile = studentProfileRepository.findByUserUsername(user.getUsername()).orElseThrow();
            reservationService.enqueue(bookId, profile.getId());
            ra.addFlashAttribute("success", "Added to reservation queue");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/books/" + bookId;
    }

    @PostMapping("/reservations/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            reservationService.cancel(id);
            ra.addFlashAttribute("success", "Reservation cancelled");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student";
    }

    @PostMapping("/reservations/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            reservationService.delete(id);
            ra.addFlashAttribute("success", "Reservation deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student";
    }
}
