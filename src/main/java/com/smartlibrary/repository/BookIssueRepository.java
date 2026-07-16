package com.smartlibrary.repository;

import com.smartlibrary.entity.BookIssue;
import com.smartlibrary.model.IssueStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookIssueRepository extends JpaRepository<BookIssue, Long> {

    List<BookIssue> findByBook_Id(Long bookId);

    @Query("""
            SELECT bi FROM BookIssue bi
            JOIN FETCH bi.book b
            LEFT JOIN FETCH b.ebook
            LEFT JOIN FETCH b.category
            LEFT JOIN FETCH b.author
            WHERE bi.student.id = :studentId
            ORDER BY bi.issuedAt DESC
            """)
    List<BookIssue> findByStudent_IdOrderByIssuedAtDesc(@Param("studentId") Long studentProfileId);

    @Query("SELECT bi FROM BookIssue bi JOIN FETCH bi.book JOIN FETCH bi.student WHERE bi.status = :status")
    List<BookIssue> findByStatusWithDetails(@Param("status") IssueStatus status);

    @Query("""
            SELECT bi FROM BookIssue bi
            WHERE bi.student.id = :studentId AND bi.book.id = :bookId
            AND bi.status IN (:activeStatuses)
            """)
    List<BookIssue> findActiveIssuesByStudentAndBook(
            @Param("studentId") Long studentId,
            @Param("bookId") Long bookId,
            @Param("activeStatuses") List<IssueStatus> activeStatuses);

    @Query("""
            SELECT bi.book.id, COUNT(bi) FROM BookIssue bi
            GROUP BY bi.book.id ORDER BY COUNT(bi) DESC
            """)
    List<Object[]> countIssuesByBook();

    long countByStatus(IssueStatus status);

    long count();

    @Query("SELECT bi FROM BookIssue bi JOIN FETCH bi.book WHERE bi.student.id = :sid ORDER BY bi.issuedAt DESC")
    List<BookIssue> historyForStudent(@Param("sid") Long studentProfileId);

    @Query("""
            SELECT bi FROM BookIssue bi
            JOIN FETCH bi.book
            JOIN FETCH bi.student s
            JOIN FETCH s.user
            WHERE bi.status = :st AND bi.dueAt >= :start AND bi.dueAt < :end
            """)
    List<BookIssue> findDueOnDate(
            @Param("st") IssueStatus st,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT bi FROM BookIssue bi JOIN FETCH bi.book JOIN FETCH bi.student WHERE bi.id = :id")
    Optional<BookIssue> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT bi FROM BookIssue bi JOIN FETCH bi.book JOIN FETCH bi.student s JOIN FETCH s.user WHERE bi.id = :id")
    Optional<BookIssue> findByIdWithBookStudentUser(@Param("id") Long id);

    @Query(value = """
            SELECT EXTRACT(ISODOW FROM bi.issued_at)::int AS dow, COUNT(*) AS cnt
            FROM book_issues bi
            GROUP BY EXTRACT(ISODOW FROM bi.issued_at)
            """, nativeQuery = true)
    List<Object[]> countIssuesByDayOfWeek();

    @Query(value = """
            SELECT bi.issued_at::date AS issue_date, COUNT(*) AS cnt
            FROM book_issues bi
            WHERE bi.issued_at >= :since
            GROUP BY bi.issued_at::date
            ORDER BY issue_date ASC
            """, nativeQuery = true)
    List<Object[]> countIssuesByDateSince(@Param("since") LocalDateTime since);

    @Query("""
            SELECT bi FROM BookIssue bi
            JOIN FETCH bi.book
            JOIN FETCH bi.student s
            JOIN FETCH s.user
            ORDER BY bi.issuedAt DESC
            """)
    List<BookIssue> findRecentWithDetails(Pageable pageable);

    long countByStudent_Id(Long studentId);

    boolean existsByStudent_IdAndStatusIn(Long studentId, List<IssueStatus> statuses);
}
