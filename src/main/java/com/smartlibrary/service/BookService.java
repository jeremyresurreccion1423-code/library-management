package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.entity.*;
import com.smartlibrary.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class  BookService {

    private static final Logger logger = LoggerFactory.getLogger(BookService.class);

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final AuthorRepository authorRepository;
    private final EbookAssetRepository ebookAssetRepository;
    private final BookIssueRepository bookIssueRepository;
    private final ReservationRepository reservationRepository;
    private final LibraryProperties libraryProperties;
    private final QrCodeService qrCodeService;

    public BookService(
            BookRepository bookRepository,
            CategoryRepository categoryRepository,
            AuthorRepository authorRepository,
            EbookAssetRepository ebookAssetRepository,
            BookIssueRepository bookIssueRepository,
            ReservationRepository reservationRepository,
            LibraryProperties libraryProperties,
            QrCodeService qrCodeService) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.authorRepository = authorRepository;
        this.ebookAssetRepository = ebookAssetRepository;
        this.bookIssueRepository = bookIssueRepository;
        this.reservationRepository = reservationRepository;
        this.libraryProperties = libraryProperties;
        this.qrCodeService = qrCodeService;
    }

    public List<Book> search(String q, Long categoryId, Long authorId, Boolean onlyAvailable) {
        String term = (q == null || q.isBlank()) ? null : q.trim();
        return bookRepository.search(term, categoryId, authorId, onlyAvailable);
    }

    public List<Book> findByIsbn(String isbn) {
        return bookRepository.findAllByIsbnExact(isbn.trim());
    }

    public List<Book> listDigitalBooksForStudent(Long studentProfileId) {
        return bookRepository.findDigitalBooksBorrowedByStudent(Objects.requireNonNull(studentProfileId));
    }

    public Optional<Book> findById(Long id) {
        return bookRepository.findByIdWithDetails(id);
    }

    public Optional<Book> findByBarcode(String barcode) {
        return bookRepository.findByBarcode(barcode == null ? "" : barcode.trim());
    }

    public Optional<Book> findByQrPayload(String payload) {
        return bookRepository.findByQrPayload(payload);
    }

    public Optional<Book> findByQrPayloadIgnoreCase(String payload) {
        return bookRepository.findByQrPayloadIgnoreCase(payload);
    }

    public List<Book> findAll() {
        return bookRepository.findAllWithDetails();
    }

    @Transactional
    public Book saveBook(
            Long id,
            String isbn,
            String title,
            String barcode,
            int totalCopies,
            Long categoryId,
            Long authorId,
            BigDecimal finePerDay) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        if (totalCopies < 1) {
            throw new IllegalArgumentException("Total copies must be at least 1");
        }
        if (finePerDay != null && finePerDay.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fine per day cannot be negative");
        }
        Book book = id == null ? new Book() : bookRepository.findById(id).orElseThrow();
        book.setIsbn(isbn);
        book.setTitle(title.trim());
        book.setBarcode(barcode != null && !barcode.isBlank() ? barcode.trim() : null);
        book.setCategory(categoryId != null ? categoryRepository.findById(categoryId).orElse(null) : null);
        book.setAuthor(authorId != null ? authorRepository.findById(authorId).orElse(null) : null);
        book.setFinePerDay(finePerDay);
        
        if (id == null) {
            book.setTotalCopies(totalCopies);
            book.setAvailableCopies(totalCopies);
        } else {
            int borrowed = book.getTotalCopies() - book.getAvailableCopies();
            book.setTotalCopies(totalCopies);
            book.setAvailableCopies(Math.max(0, totalCopies - borrowed));
        }
        
        if (book.getAvailableCopies() > book.getTotalCopies()) {
            throw new IllegalStateException("Available copies cannot exceed total copies");
        }
        
        book = bookRepository.save(book);

        String payload = qrCodeService.bookPayload(book.getId());
        book.setQrPayload(payload);
        
        logger.info("Book {} saved: {} by {}", book.getId(), book.getTitle(), book.getAuthor());
        return bookRepository.save(book);
    }

    @Transactional
    public void deleteBook(Long id) {
        Long bookId = Objects.requireNonNull(id);

        List<BookIssue> issues = bookIssueRepository.findByBook_Id(bookId);
        if (!issues.isEmpty()) {
            bookIssueRepository.deleteAll(issues);
        }

        List<Reservation> reservations = reservationRepository.findByBook_Id(bookId);
        if (!reservations.isEmpty()) {
            reservationRepository.deleteAll(reservations);
        }

        ebookAssetRepository.findByBook_Id(bookId).ifPresent(ebookAssetRepository::delete);

        bookRepository.deleteById(bookId);
        logger.info("Book {} deleted with all associated data", bookId);
    }

    @Transactional
    public void attachEbook(Long bookId, MultipartFile file) throws IOException {
        Book book = bookRepository.findById(Objects.requireNonNull(bookId))
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF e-books are allowed");
        }

        Path dir = Path.of(libraryProperties.getUploadDir());
        Files.createDirectories(dir);

        String stored = UUID.randomUUID() + ".pdf";
        Path target = dir.resolve(stored);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        EbookAsset asset = ebookAssetRepository.findByBook_Id(bookId).orElse(new EbookAsset());
        asset.setBook(book);
        asset.setOriginalFilename(originalFilename);
        asset.setStoredPath(target.toAbsolutePath().toString());
        ebookAssetRepository.save(asset);
        book.setEbook(asset);
        
        logger.info("E-book attached to book {}: {}", bookId, originalFilename);
    }

    public Optional<EbookAsset> ebookForBook(Long bookId) {
        return ebookAssetRepository.findByBook_Id(bookId);
    }

    public Path resolveEbookPath(EbookAsset asset) {
        return Path.of(asset.getStoredPath());
    }
}
