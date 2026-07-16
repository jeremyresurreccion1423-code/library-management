package com.smartlibrary.repository;

import com.smartlibrary.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface  BookRepository extends JpaRepository<Book, Long> {

    @Query("SELECT b FROM Book b WHERE UPPER(b.barcode) = UPPER(:barcode)")
    Optional<Book> findByBarcode(@Param("barcode") String barcode);

    @Query("SELECT b FROM Book b WHERE b.qrPayload = :payload")
    Optional<Book> findByQrPayload(@Param("payload") String payload);

    @Query("SELECT b FROM Book b WHERE UPPER(b.qrPayload) = UPPER(:payload)")
    Optional<Book> findByQrPayloadIgnoreCase(@Param("payload") String payload);

    @Query("""
            SELECT b FROM Book b
            LEFT JOIN FETCH b.category
            LEFT JOIN FETCH b.author
            LEFT JOIN FETCH b.ebook
            WHERE (:q IS NULL OR :q = '' OR LOWER(b.title) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(b.isbn) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:categoryId IS NULL OR b.category.id = :categoryId)
            AND (:authorId IS NULL OR b.author.id = :authorId)
            AND (:onlyAvailable IS NULL OR :onlyAvailable = false OR b.availableCopies > 0)
            """)
    List<Book> search(
            @Param("q") String q,
            @Param("categoryId") Long categoryId,
            @Param("authorId") Long authorId,
            @Param("onlyAvailable") Boolean onlyAvailable);

    @Query("SELECT b FROM Book b LEFT JOIN FETCH b.category LEFT JOIN FETCH b.author LEFT JOIN FETCH b.ebook WHERE b.isbn = :isbn")
    List<Book> findAllByIsbnExact(@Param("isbn") String isbn);

    @Query("SELECT DISTINCT b FROM Book b JOIN b.ebook e")
    List<Book> findAllWithEbook();

    @Query("""
            SELECT DISTINCT b FROM Book b
            JOIN FETCH b.ebook
            LEFT JOIN FETCH b.author
            LEFT JOIN FETCH b.category
            WHERE b.id IN (
                SELECT bi.book.id FROM BookIssue bi
                WHERE bi.student.id = :studentProfileId
                AND bi.status IN (com.smartlibrary.model.IssueStatus.BORROWED, com.smartlibrary.model.IssueStatus.OVERDUE)
            )
            """)
    List<Book> findDigitalBooksBorrowedByStudent(@Param("studentProfileId") Long studentProfileId);

    @Query("SELECT b FROM Book b LEFT JOIN FETCH b.category LEFT JOIN FETCH b.author WHERE b.id = :id")
    Optional<Book> findByIdWithDetails(@Param("id") Long id);

    long countByCategory_Id(Long categoryId);

    @Query("SELECT b FROM Book b LEFT JOIN FETCH b.category LEFT JOIN FETCH b.author")
    List<Book> findAllWithDetails();
}
