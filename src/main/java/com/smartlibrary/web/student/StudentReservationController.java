package com.smartlibrary.web.student;

import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.BookIssueService;
import com.smartlibrary.service.ReservationService;
import com.smartlibrary.service.SharedLibraryStudentProfileBridgeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
@RequestMapping("/student")
public class StudentReservationController {

    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService;
    private final ReservationService reservationService;
    private final BookIssueService bookIssueService;

    public StudentReservationController(
            SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService,
            ReservationService reservationService,
            BookIssueService bookIssueService) {
        this.sharedLibraryStudentProfileBridgeService = sharedLibraryStudentProfileBridgeService;
        this.reservationService = reservationService;
        this.bookIssueService = bookIssueService;
    }

    @PostMapping("/borrow")
    public String borrow(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam Long bookId,
            RedirectAttributes ra) {
        try {
            var profile = sharedLibraryStudentProfileBridgeService.ensureLibraryStudentProfile(user.getUser())
                    .orElseThrow(() -> new IllegalStateException("Student profile not found for this account. Please login as a student user."));
            var issue = bookIssueService.issueToStudent(bookId, profile);
            String dueDate = issue.getDueAt().format(DUE_DATE);
            ra.addFlashAttribute("success", "Book borrowed successfully! Return by " + dueDate + ".");
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
            var profile = sharedLibraryStudentProfileBridgeService.ensureLibraryStudentProfile(user.getUser()).orElseThrow();
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

    @PostMapping("/reservations/bulk-delete")
    public String bulkDelete(
            @AuthenticationPrincipal LibraryUserDetails user,
            @RequestParam(name = "ids", required = false) List<Long> ids,
            RedirectAttributes ra) {
        try {
            var profile = sharedLibraryStudentProfileBridgeService.ensureLibraryStudentProfile(user.getUser()).orElseThrow();
            if (ids == null || ids.isEmpty()) {
                ra.addFlashAttribute("error", "Select at least one reservation record to delete.");
                return "redirect:/student";
            }
            int count = reservationService.deleteHistoryForStudent(ids, profile.getId());
            if (count == 0) {
                ra.addFlashAttribute("error", "No reservation history records were deleted.");
            } else {
                ra.addFlashAttribute("success", count + " reservation record(s) deleted.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student";
    }
}
