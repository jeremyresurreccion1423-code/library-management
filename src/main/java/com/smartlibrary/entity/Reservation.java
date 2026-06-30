package com.smartlibrary.entity;

import com.smartlibrary.model.ReservationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(
        name = "reservations",
        indexes = {
                @Index(name = "idx_reservations_book_status_queue", columnList = "book_id,status,queueOrder"),
                @Index(name = "idx_reservations_student_status", columnList = "student_profile_id,status")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"book", "student"})
public class Reservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_profile_id")
    private StudentProfile student;

    @Column(nullable = false)
    private int queueOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status = ReservationStatus.WAITING;
}
