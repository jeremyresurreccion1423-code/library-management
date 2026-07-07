package com.smartlibrary.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class BookForm {

    private Long id;

    @NotBlank(message = "ISBN cannot be blank")
    private String isbn;

    @NotBlank(message = "Title cannot be blank")
    private String title;

    private String barcode;

    @NotNull(message = "Total copies are required")
    @Min(value = 0, message = "Total copies cannot be negative")
    private Integer totalCopies;

    private Long categoryId;

    private Long authorId;

    @Min(value = 0, message = "Fine per day cannot be negative")
    private BigDecimal finePerDay;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Integer getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(Integer totalCopies) {
        this.totalCopies = totalCopies;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public BigDecimal getFinePerDay() {
        return finePerDay;
    }

    public void setFinePerDay(BigDecimal finePerDay) {
        this.finePerDay = finePerDay;
    }
}
