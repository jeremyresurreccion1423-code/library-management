package com.smartlibrary.repository;

import com.smartlibrary.entity.Reservation;
import com.smartlibrary.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByBook_Id(Long bookId);

    List<Reservation> findByBook_IdAndStatusOrderByQueueOrderAsc(Long bookId, ReservationStatus status);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.book WHERE r.student.id = :studentId ORDER BY r.createdAt DESC")
    List<Reservation> findByStudent_IdOrderByCreatedAtDesc(@Param("studentId") Long studentProfileId);

    int countByBook_IdAndStatus(Long bookId, ReservationStatus status);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.book JOIN FETCH r.student ORDER BY r.createdAt DESC")
    List<Reservation> findAllWithDetails();

    @Query("SELECT r FROM Reservation r JOIN FETCH r.book JOIN FETCH r.student WHERE r.id = :id")
    Optional<Reservation> findByIdWithDetails(@Param("id") Long id);

    long countByStudent_Id(Long studentId);

    long countByStatus(ReservationStatus status);
}
