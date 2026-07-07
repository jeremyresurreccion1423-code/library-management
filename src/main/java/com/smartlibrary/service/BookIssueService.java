package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.BookIssue;
import com.smartlibrary.entity.Reservation;
import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.model.ReservationStatus;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.ReservationRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.AdminRevenueRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BookIssueService {

    private static final Logger logger = LoggerFactory.getLogger(BookIssueService.class);

    private final BookIssueRepository bookIssueRepository;
    private final BookRepository bookRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final LibraryProperties libraryProperties;
    private final FineCalculator fineCalculator;
    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final AdminRevenueService adminRevenueService;
    private final AdminRevenueRepository adminRevenueRepository;

    public BookIssueService(
            BookIssueRepository bookIssueRepository,
            BookRepository bookRepository,
            StudentProfileRepository studentProfileRepository,
            LibraryProperties libraryProperties,
            FineCalculator fineCalculator,
            ReservationService reservationService,
            ReservationRepository reservationRepository,
            AdminRevenueService adminRevenueService,
            AdminRevenueRepository adminRevenueRepository) {
        this.bookIssueRepository = bookIssueRepository;
        this.bookRepository = bookRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.libraryProperties = libraryProperties;
        this.fineCalculator = fineCalculator;
        this.reservationService = reservationService;
        this.reservationRepository = reservationRepository;
        this.adminRevenueService = adminRevenueService;
        this.adminRevenueRepository = adminRevenueRepository;
    }

    @Transactional
    public BookIssue issueToStudent(Long bookId, String studentIdOrUsername) {
        StudentProfile student = studentProfileRepository
                .findByStudentId(studentIdOrUsername.trim())
                .or(() -> studentProfileRepository.findByUserUsername(studentIdOrUsername.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        return issueToStudent(bookId, student);
    }

    @Transactional
    public BookIssue issueToStudent(Long bookId, StudentProfile student) {
        Objects.requireNonNull(student.getId(), "Student profile id is required");
        StudentProfile managedStudent = studentProfileRepository.findById(student.getId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        return createIssue(bookId, managedStudent);
    }

    private BookIssue createIssue(Long bookId, StudentProfile student) {
        Book book = bookRepository.findById(Objects.requireNonNull(bookId))
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));

        if (book.getAvailableCopies() <= 0) {
            throw new IllegalStateException("No copies available");
        }

        List<BookIssue> activeIssues = bookIssueRepository.findActiveIssuesByStudentAndBook(
                student.getId(), bookId, List.of(IssueStatus.BORROWED, IssueStatus.OVERDUE));
        if (!activeIssues.isEmpty()) {
            throw new IllegalStateException("You already have this book borrowed");
        }

        LocalDateTime now = LocalDateTime.now();
        BookIssue issue = new BookIssue();
        issue.setBook(book);
        issue.setStudent(student);
        issue.setIssuedAt(now);
        issue.setDueAt(now.plusDays(libraryProperties.getLoanDays()));
        issue.setStatus(IssueStatus.BORROWED);
        
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);  // Version will be incremented automatically
        
        BookIssue savedIssue = bookIssueRepository.save(issue);
        logger.info("Book {} issued to student {} with issue ID {}", bookId, student.getStudentId(), savedIssue.getId());
        return savedIssue;
    }

    @Transactional
    public BookIssue returnBook(Long issueId) {
        BookIssue issue = bookIssueRepository.findById(Objects.requireNonNull(issueId))
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));
        
        if (issue.getStatus() == IssueStatus.RETURNED) {
            throw new IllegalStateException("Already returned");
        }
        
        LocalDateTime now = LocalDateTime.now();
        issue.setReturnedAt(now);
        issue.setFineAmount(fineCalculator.computeFine(issue.getBook(), issue.getDueAt(), now));
        issue.setStatus(IssueStatus.RETURNED);
        
        Book book = issue.getBook();
        book.setAvailableCopies(book.getAvailableCopies() + 1);
        bookRepository.save(book);
        bookIssueRepository.save(issue);
        
        adminRevenueService.createRevenueFromBookReturn(issue);
        if (issue.getFineAmount() != null && issue.getFineAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            adminRevenueService.createRevenueFromFine(issue);
        }
        
        reservationService.notifyNextInQueue(book.getId());
        logger.info("Book {} returned by student {} with fine {}", book.getId(), issue.getStudent().getStudentId(), issue.getFineAmount());
        return issue;
    }

    @Transactional
    public BookIssue issueFromReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdWithDetails(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        
        if (reservation.getStatus() != ReservationStatus.WAITING) {
            throw new IllegalStateException("Reservation is not in waiting status");
        }
        if (reservation.getBook() == null || reservation.getStudent() == null) {
            throw new IllegalStateException("Reservation is missing book or student data");
        }
        
        String studentId = reservation.getStudent().getStudentId();
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalStateException("Student ID is missing");
        }
        
        BookIssue issue = issueToStudent(reservation.getBook().getId(), studentId);
        reservation.setStatus(ReservationStatus.FULFILLED);
        reservationRepository.save(reservation);
        reservationService.reorderQueue(reservation.getBook().getId());
        
        return issue;
    }

    @Transactional
    public int markOverdue() {
        List<BookIssue> borrowed = bookIssueRepository.findByStatusWithDetails(IssueStatus.BORROWED);
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        
        for (BookIssue bi : borrowed) {
            if (bi.getDueAt().isBefore(now)) {
                bi.setStatus(IssueStatus.OVERDUE);
                bookIssueRepository.save(bi);
                count++;
            }
        }
        
        logger.info("Marked {} issues as overdue", count);
        return count;
    }

    public List<BookIssue> issuesForStudent(Long studentProfileId) {
        return bookIssueRepository.findByStudent_IdOrderByIssuedAtDesc(studentProfileId);
    }

    public List<BookIssue> allActiveIssues() {
        return bookIssueRepository.findByStatusWithDetails(IssueStatus.BORROWED);
    }

    public List<BookIssue> allOverdue() {
        return bookIssueRepository.findByStatusWithDetails(IssueStatus.OVERDUE);
    }

    public Optional<BookIssue> findById(Long id) {
        return bookIssueRepository.findByIdWithDetails(Objects.requireNonNull(id));
    }

    public Optional<BookIssue> findByIdWithBookStudentUser(Long id) {
        return bookIssueRepository.findByIdWithBookStudentUser(Objects.requireNonNull(id));
    }

    @Transactional
    public void deleteReturnedHistoryForStudent(Long issueId, Long studentProfileId) {
        BookIssue issue = bookIssueRepository.findById(Objects.requireNonNull(issueId))
                .orElseThrow(() -> new IllegalArgumentException("History record not found"));
        
        if (!issue.getStudent().getId().equals(Objects.requireNonNull(studentProfileId))) {
            throw new IllegalArgumentException("You are not allowed to delete this history record.");
        }
        if (issue.getStatus() != IssueStatus.RETURNED) {
            throw new IllegalStateException("Only returned book history can be deleted.");
        }
        
        adminRevenueRepository.deleteByBookIssueId(issue.getId());
        bookIssueRepository.delete(issue);
        logger.info("History record deleted for issue {}", issueId);
    }

    @Transactional
    public int returnBooksForStudent(List<Long> issueIds, Long studentProfileId) {
        if (issueIds == null || issueIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long issueId : issueIds) {
            if (issueId == null) {
                continue;
            }
            BookIssue issue = bookIssueRepository.findById(issueId).orElse(null);
            if (issue == null || !issue.getStudent().getId().equals(studentProfileId)) {
                continue;
            }
            if (issue.getStatus() == IssueStatus.RETURNED) {
                continue;
            }
            returnBook(issueId);
            count++;
        }
        return count;
    }

    @Transactional
    public int deleteReturnedHistoryForStudent(List<Long> issueIds, Long studentProfileId) {
        if (issueIds == null || issueIds.isEmpty()) {
            return 0;
        }
        List<Long> uniqueIds = issueIds.stream().filter(Objects::nonNull).distinct().toList();
        int count = 0;
        for (Long issueId : uniqueIds) {
            try {
                deleteReturnedHistoryForStudent(issueId, studentProfileId);
                count++;
            } catch (RuntimeException ex) {
                logger.warn("Skipped history delete for issue {}: {}", issueId, ex.getMessage());
            }
        }
        return count;
    }
}
