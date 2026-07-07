package com.smartlibrary.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(
        name = "books",
        indexes = {
                @Index(name = "idx_books_title", columnList = "title"),
                @Index(name = "idx_books_available_copies", columnList = "availableCopies"),
                @Index(name = "idx_books_category", columnList = "category_id"),
                @Index(name = "idx_books_author", columnList = "author_id")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"category", "author", "ebook"})
public class Book extends BaseEntity {

    @Column(length = 64)
    private String isbn;

    @NotBlank(message = "Title cannot be blank")
    @Column(nullable = false, length = 512)
    private String title;

    @Column(unique = true, length = 256)
    private String barcode;

    @Column(length = 1024)
    private String qrPayload;

    @Min(0)
    @Column(nullable = false)
    private int availableCopies;

    @Min(value = 0, message = "Total copies cannot be negative")
    @Column(nullable = false)
    private int totalCopies;

    /** Overdue fine per day in PHP. If null, the library-wide default applies. */
    @Column(precision = 10, scale = 2)
    private BigDecimal finePerDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;

    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private EbookAsset ebook;

    public boolean isAvailable() {
        return availableCopies > 0;
    }

    public void setEbook(EbookAsset ebook) {
        this.ebook = ebook;
        if (ebook != null) {
            ebook.setBook(this);
        }
    }
}
