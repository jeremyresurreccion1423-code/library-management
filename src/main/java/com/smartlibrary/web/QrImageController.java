package com.smartlibrary.web;

import com.smartlibrary.service.BookService;
import com.smartlibrary.service.QrCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QrImageController {

    private static final Logger logger = LoggerFactory.getLogger(QrImageController.class);
    private static final int QR_SIZE = 240;

    private final BookService bookService;
    private final QrCodeService qrCodeService;

    public QrImageController(BookService bookService, QrCodeService qrCodeService) {
        this.bookService = bookService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping(value = "/api/books/{id}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qrPng(@PathVariable Long id) throws Exception {
        var book = bookService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with ID: " + id));
        
        String payload = book.getQrPayload() != null ? book.getQrPayload() : qrCodeService.bookPayload(id);
        
        logger.debug("Generating QR code PNG for book {}: payload='{}'", id, payload);
        
        byte[] pngBytes = qrCodeService.pngBytes(payload, QR_SIZE, QR_SIZE);
        
        logger.debug("QR code PNG generated: {} bytes for book {}", pngBytes.length, id);
        
        return pngBytes;
    }
}
