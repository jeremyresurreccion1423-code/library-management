package com.smartlibrary.web.admin;

import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.ReservationService;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
@RequestMapping("/admin/issues")
public class AdminIssueController {

    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final BookIssueService bookIssueService;
    private final BookService bookService;
    private final ReservationService reservationService;

    public AdminIssueController(BookIssueService bookIssueService, BookService bookService, ReservationService reservationService) {
        this.bookIssueService = bookIssueService;
        this.bookService = bookService;
        this.reservationService = reservationService;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public String list(
            @RequestParam(value = "bookId", required = false) Long bookId,
            Model model) {
        model.addAttribute("active", bookIssueService.allActiveIssues());
        model.addAttribute("overdue", bookIssueService.allOverdue());
        model.addAttribute("books", bookService.search(null, null, null, null));
        model.addAttribute("selectedBookId", bookId);
        return "admin/issues";
    }

    @PostMapping("/issue")
    public String issue(@RequestParam Long bookId, @RequestParam String studentId, RedirectAttributes ra) {
        try {
            var issue = bookIssueService.issueToStudent(bookId, studentId);
            ra.addFlashAttribute("success", "Book issued. Return by " + issue.getDueAt().format(DUE_DATE) + ".");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/issues";
    }

    @PostMapping("/{id}/return")
    public String returnBook(@PathVariable Long id, RedirectAttributes ra) {
        try {
            var issue = bookIssueService.returnBook(id);
            StringBuilder message = new StringBuilder("Book returned successfully!");

            if (issue.getFineAmount() != null && issue.getFineAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                message.append(" Fine collected: ₱").append(issue.getFineAmount());
            }
            ra.addFlashAttribute("success", message.toString());
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/issues";
    }

    @Transactional(readOnly = true)
    @GetMapping("/reservations")
    public String reservations(Model model) {
        model.addAttribute("reservations", reservationService.allReservations());
        return "admin/reservations";
    }

    @PostMapping("/reservations/{id}/issue")
    public String issueFromReservation(@PathVariable Long id, RedirectAttributes ra) {
        try {
            bookIssueService.issueFromReservation(id);
            ra.addFlashAttribute("success", "Successfully issued");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/issues/reservations";
    }
}
