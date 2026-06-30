package com.smartlibrary.service;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.Reservation;
import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.model.ReservationStatus;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.ReservationRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

@Service
public class ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final StudentProfileRepository studentProfileRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            BookRepository bookRepository,
            StudentProfileRepository studentProfileRepository) {
        this.reservationRepository = reservationRepository;
        this.bookRepository = bookRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    @Transactional
    public Reservation enqueue(Long bookId, Long studentProfileId) {
        Book book = bookRepository.findById(Objects.requireNonNull(bookId))
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        StudentProfile student = studentProfileRepository.findById(Objects.requireNonNull(studentProfileId))
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
        
        // Calculate next queue position
        int next = reservationRepository.countByBook_IdAndStatus(bookId, ReservationStatus.WAITING) + 1;
        
        Reservation r = new Reservation();
        r.setBook(book);
        r.setStudent(student);
        r.setQueueOrder(next);
        r.setStatus(ReservationStatus.WAITING);
        
        Reservation saved = reservationRepository.save(r);
        logger.info("Book {} reserved by student {} with queue order {}", bookId, student.getStudentId(), next);
        return saved;
    }

    public List<Reservation> queueForBook(Long bookId) {
        return reservationRepository.findByBook_IdAndStatusOrderByQueueOrderAsc(bookId, ReservationStatus.WAITING);
    }

    public List<Reservation> forStudent(Long studentProfileId) {
        return reservationRepository.findByStudent_IdOrderByCreatedAtDesc(studentProfileId);
    }

    public List<Reservation> allReservations() {
        return reservationRepository.findAllWithDetails();
    }

    @Transactional
    public void cancel(Long reservationId) {
        Reservation r = reservationRepository.findById(Objects.requireNonNull(reservationId))
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
        r.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(r);
        reorderQueue(r.getBook().getId());
        logger.info("Reservation {} cancelled for student {}", reservationId, r.getStudent().getStudentId());
    }

    @Transactional
    public void delete(Long reservationId) {
        reservationRepository.deleteById(Objects.requireNonNull(reservationId));
    }

    @Transactional
    public void notifyNextInQueue(Long bookId) {
        List<Reservation> waiting = queueForBook(bookId);
        if (waiting.isEmpty()) {
            return;
        }
        
        Reservation first = waiting.get(0);
        Book book = bookRepository.findById(Objects.requireNonNull(bookId))
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
        
        if (book.getAvailableCopies() > 0) {
            first.setStatus(ReservationStatus.FULFILLED);
            reservationRepository.save(first);
            logger.info("Reservation {} for student {} fulfilled", first.getId(), first.getStudent().getStudentId());
        }
    }

    @Transactional
    public void reorderQueue(Long bookId) {
        List<Reservation> list = reservationRepository.findByBook_IdAndStatusOrderByQueueOrderAsc(bookId, ReservationStatus.WAITING);
        int i = 1;
        for (Reservation r : list) {
            r.setQueueOrder(i++);
            reservationRepository.save(r);
        }
        logger.info("Queue reordered for book {} with {} waiting reservations", bookId, list.size());
    }
}
