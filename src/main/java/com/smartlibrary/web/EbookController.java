package com.smartlibrary.web;

import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.BookService;
import com.smartlibrary.service.SharedLibraryStudentProfileBridgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@Controller
public class EbookController {

    private static final Logger logger = LoggerFactory.getLogger(EbookController.class);
    private static final List<IssueStatus> ACTIVE_LOAN_STATUSES = List.of(IssueStatus.BORROWED, IssueStatus.OVERDUE);

    private final BookService bookService;
    private final SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService;
    private final BookIssueRepository bookIssueRepository;

    public EbookController(
            BookService bookService,
            SharedLibraryStudentProfileBridgeService sharedLibraryStudentProfileBridgeService,
            BookIssueRepository bookIssueRepository) {
        this.bookService = bookService;
        this.sharedLibraryStudentProfileBridgeService = sharedLibraryStudentProfileBridgeService;
        this.bookIssueRepository = bookIssueRepository;
    }

    @GetMapping("/ebook/read/{bookId}")
    public String reader(@AuthenticationPrincipal LibraryUserDetails user, @PathVariable("bookId") Long bookId, Model model) {
        if (!canAccessEbook(user, bookId)) {
            logger.warn("Unauthorized ebook access attempt for book {} by user {}", bookId, user != null ? user.getUsername() : "anonymous");
            return "redirect:/books/" + bookId + "?error=ebook-access";
        }

        var opt = bookService.ebookForBook(bookId);
        if (opt.isEmpty()) {
            logger.info("No ebook found for book {}", bookId);
            return "redirect:/books/" + bookId + "?error=no-ebook";
        }

        model.addAttribute("bookId", bookId);
        model.addAttribute("filename", opt.get().getOriginalFilename());
        logger.debug("E-book reader loaded for book {} by user {}", bookId, user.getUsername());

        return "ebook-reader";
    }

    @GetMapping("/ebook/file/{bookId}")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal LibraryUserDetails user, @PathVariable("bookId") Long bookId) {
        if (!canAccessEbook(user, bookId)) {
            logger.warn("Unauthorized ebook file download attempt for book {} by user {}", bookId, user != null ? user.getUsername() : "anonymous");
            return ResponseEntity.status(403).build();
        }

        var asset = bookService.ebookForBook(bookId)
                .orElse(null);
        if (asset == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path path = bookService.ensureEbookFileOnDisk(bookId);
            if (!Files.exists(path)) {
                logger.warn("E-book file still missing after restore for book {}", bookId);
                return ResponseEntity.notFound().build();
            }

            Resource res = new FileSystemResource(path);
            logger.info("E-book file served for book {} by user {}: {}", bookId, user.getUsername(), path.getFileName());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + asset.getOriginalFilename() + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(res);
        } catch (Exception e) {
            logger.error("Failed to serve e-book for book {}: {}", bookId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean canAccessEbook(LibraryUserDetails userDetails, Long bookId) {
        if (userDetails == null) {
            return false;
        }

        if (userDetails.getUser().getRole() == UserRole.ADMIN
                || userDetails.getUser().getRole() == UserRole.SUPER_ADMIN) {
            return bookService.ebookForBook(bookId).isPresent();
        }

        var profileOpt = sharedLibraryStudentProfileBridgeService.ensureLibraryStudentProfile(userDetails.getUser());
        if (profileOpt.isEmpty()) {
            return false;
        }

        if (bookService.ebookForBook(bookId).isEmpty()) {
            return false;
        }

        return !bookIssueRepository.findActiveIssuesByStudentAndBook(
                profileOpt.get().getId(), bookId, ACTIVE_LOAN_STATUSES).isEmpty();
    }
}
