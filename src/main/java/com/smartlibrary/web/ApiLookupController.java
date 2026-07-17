package com.smartlibrary.web;

import com.smartlibrary.entity.Book;
import com.smartlibrary.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiLookupController {

    private static final Logger log = LoggerFactory.getLogger(ApiLookupController.class);

    private final BookService bookService;

    public ApiLookupController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/lookup/any")
    public ResponseEntity<?> byAny(@RequestParam String code) {
        String c = code == null ? "" : code.trim();

        if (c.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (c.toUpperCase().startsWith("BOOK:")) {
            String idStr = c.substring("BOOK:".length()).trim();
            try {
                Long id = Long.parseLong(idStr);
                Optional<Book> result = bookService.findById(id);
                if (result.isPresent()) {
                    return ResponseEntity.ok(bookToMap(result.get()));
                }
            } catch (NumberFormatException ignored) {
                // Not a numeric id after BOOK: prefix; fall through to other strategies.
            }
        }

        try {
            Long id = Long.parseLong(c);
            Optional<Book> result = bookService.findById(id);
            if (result.isPresent()) {
                return ResponseEntity.ok(bookToMap(result.get()));
            }
        } catch (NumberFormatException ignored) {
            // Not a numeric id; fall through to payload/barcode strategies.
        }

        Optional<Book> result = bookService.findByQrPayload(c);
        if (result.isPresent()) {
            return ResponseEntity.ok(bookToMap(result.get()));
        }

        result = bookService.findByQrPayloadIgnoreCase(c);
        if (result.isPresent()) {
            return ResponseEntity.ok(bookToMap(result.get()));
        }

        result = bookService.findByBarcode(c);
        if (result.isPresent()) {
            return ResponseEntity.ok(bookToMap(result.get()));
        }

        String normalized = c.toUpperCase().replaceAll("[^A-Z0-9]", "");

        for (Book b : bookService.findAll()) {
            if (b.getQrPayload() != null) {
                String dbNormalized = b.getQrPayload().toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (dbNormalized.equals(normalized)) {
                    return ResponseEntity.ok(bookToMap(b));
                }
            }
            if (b.getBarcode() != null) {
                String dbNormalized = b.getBarcode().toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (dbNormalized.equals(normalized)) {
                    return ResponseEntity.ok(bookToMap(b));
                }
            }
        }

        log.warn("Book lookup failed for code: {}", c);
        return ResponseEntity.notFound().build();
    }

    private Map<String, Object> bookToMap(Book b) {
        return Map.of(
                "bookId", b.getId(),
                "title", b.getTitle(),
                "author", b.getAuthor() != null ? b.getAuthor().getName() : "Unknown",
                "availableCopies", b.getAvailableCopies(),
                "isAvailable", b.isAvailable()
        );
    }
}
