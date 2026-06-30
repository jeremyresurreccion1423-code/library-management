package com.smartlibrary.web;

import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.security.LibraryUserDetails;
import com.smartlibrary.service.BookService;
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
import java.util.Objects;

@Controller
public class EbookController {

    private static final Logger logger = LoggerFactory.getLogger(EbookController.class);

    private final BookService bookService;
    private final StudentProfileRepository studentProfileRepository;

    public EbookController(BookService bookService, StudentProfileRepository studentProfileRepository) {
        this.bookService = bookService;
        this.studentProfileRepository = studentProfileRepository;
    }

    @GetMapping("/ebook/read/{bookId}")
    public String reader(@AuthenticationPrincipal LibraryUserDetails user, @PathVariable("bookId") Long bookId, Model model) {
        if (!canAccessEbook(user, bookId)) {
            logger.warn("Unauthorized ebook access attempt for book {} by user {}", bookId, user != null ? user.getUsername() : "anonymous");
            return "redirect:/books/" + bookId;
        }
        
        var opt = bookService.ebookForBook(bookId);
        if (opt.isEmpty()) {
            logger.info("No ebook found for book {}", bookId);
            return "redirect:/books/" + bookId;
        }
        
        model.addAttribute("bookId", bookId);
        model.addAttribute("filename", opt.get().getOriginalFilename());
        logger.debug("E-book reader loaded for book {} by user {}", bookId, user.getUsername());
        
        return "ebook-reader";
    }

    @GetMapping("/ebook/file/{bookId}")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal LibraryUserDetails user, @PathVariable("bookId") Long bookId) throws Exception {
        if (!canAccessEbook(user, bookId)) {
            logger.warn("Unauthorized ebook file download attempt for book {} by user {}", bookId, user != null ? user.getUsername() : "anonymous");
            return ResponseEntity.status(403).build();
        }
        
        var asset = bookService.ebookForBook(bookId)
                .orElseThrow(() -> new IllegalArgumentException("E-book asset not found for book: " + bookId));
        
        Path path = Objects.requireNonNull(bookService.resolveEbookPath(asset), "E-book path resolution failed");
        
        if (!Files.exists(path)) {
            logger.warn("E-book file not found on disk: {} for book {}", path, bookId);
            return ResponseEntity.notFound().build();
        }
        
        Resource res = new FileSystemResource(path);
        MediaType mediaType = MediaType.APPLICATION_PDF;
        
        logger.info("E-book file served for book {} by user {}: {}", bookId, user.getUsername(), path.getFileName());
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + asset.getOriginalFilename() + "\"")
                .contentType(mediaType)
                .body(res);
    }

    private boolean canAccessEbook(LibraryUserDetails userDetails, Long bookId) {
        if (userDetails == null) {
            logger.debug("Access denied: unauthenticated user");
            return false;
        }

        if (userDetails.getUser().getRole() == UserRole.ADMIN) {
            logger.debug("Access allowed: admin user");
            return true;
        }

        var profileOpt = studentProfileRepository.findByUserUsername(userDetails.getUsername());
        if (profileOpt.isEmpty()) {
            logger.debug("Access denied: student profile not found for user {}", userDetails.getUsername());
            return false;
        }
        
        boolean hasAccess = bookService.listDigitalBooksForStudent(profileOpt.get().getId())
                .stream()
                .anyMatch(book -> Objects.equals(book.getId(), bookId));
        
        if (!hasAccess) {
            logger.debug("Access denied: book {} not in student {}'s borrowed list", bookId, userDetails.getUsername());
        }
        
        return hasAccess;
    }
}
