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
        log.info("=== Flexible lookup START === code='{}' (length={})", c, c.length());

        if (c.isEmpty()) {
            log.warn("Empty code provided");
            return ResponseEntity.notFound().build();
        }

        if (c.toUpperCase().startsWith("BOOK:")) {
            String idStr = c.substring("BOOK:".length()).trim();
            log.debug("BOOK: prefix detected, attempting numeric ID: '{}'", idStr);
            try {
                Long id = Long.parseLong(idStr);
                Optional<Book> result = bookService.findById(id);
                if (result.isPresent()) {
                    log.info("✓ Found by BOOK: prefix, bookId={}", id);
                    return ResponseEntity.ok(bookToMap(result.get()));
                }
            } catch (NumberFormatException e) {
                log.debug("Not a valid numeric ID after BOOK: prefix: '{}'", idStr);
            }
        }

        try {
            Long id = Long.parseLong(c);
            Optional<Book> result = bookService.findById(id);
            if (result.isPresent()) {
                log.info("✓ Found by raw numeric ID: {}", id);
                return ResponseEntity.ok(bookToMap(result.get()));
            }
        } catch (NumberFormatException ignored) {
            log.debug("Code is not a numeric ID: '{}'", c);
        }

        Optional<Book> result = bookService.findByQrPayload(c);
        if (result.isPresent()) {
            log.info("✓ Found by exact QR payload: '{}'", c);
            return ResponseEntity.ok(bookToMap(result.get()));
        }

        result = bookService.findByQrPayloadIgnoreCase(c);
        if (result.isPresent()) {
            log.info("✓ Found by case-insensitive QR payload: '{}'", c);
            return ResponseEntity.ok(bookToMap(result.get()));
        }

        log.debug("Attempting barcode search: '{}'", c);
        result = bookService.findByBarcode(c);
        if (result.isPresent()) {
            log.info("✓ Found by barcode: '{}'", c);
            return ResponseEntity.ok(bookToMap(result.get()));
        }
        log.debug("Barcode search failed for: '{}'", c);

        String normalized = c.toUpperCase().replaceAll("[^A-Z0-9]", "");
        log.debug("Fallback normalization: '{}' → '{}'", c, normalized);

        for (Book b : bookService.findAll()) {
            if (b.getQrPayload() != null) {
                String dbNormalized = b.getQrPayload().toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (dbNormalized.equals(normalized)) {
                    log.info("✓ Found by normalized QR payload: bookId={}", b.getId());
                    return ResponseEntity.ok(bookToMap(b));
                }
            }
            if (b.getBarcode() != null) {
                String dbNormalized = b.getBarcode().toUpperCase().replaceAll("[^A-Z0-9]", "");
                if (dbNormalized.equals(normalized)) {
                    log.info("✓ Found by normalized barcode: bookId={}", b.getId());
                    return ResponseEntity.ok(bookToMap(b));
                }
            }
        }

        log.warn("✗ Book not found for any strategy: '{}' | normalized: '{}'", c, normalized);
        return ResponseEntity.notFound().build();
    }

    private Map<String, Object> bookToMap(com.smartlibrary.entity.Book b) {
        return Map.of(
                "bookId", b.getId(),
                "title", b.getTitle(),
                "author", b.getAuthor() != null ? b.getAuthor().getName() : "Unknown",
                "availableCopies", b.getAvailableCopies(),
                "isAvailable", b.isAvailable()
        );
    }
}
