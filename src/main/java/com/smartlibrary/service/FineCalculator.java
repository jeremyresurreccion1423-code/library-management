package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.entity.Book;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class FineCalculator {

    private final LibraryProperties properties;

    public FineCalculator(LibraryProperties properties) {
        this.properties = properties;
    }

    public BigDecimal resolveFinePerDay(Book book) {
        if (book != null && book.getFinePerDay() != null) {
            return book.getFinePerDay();
        }
        return properties.getFinePerDay();
    }

    public BigDecimal computeFine(Book book, LocalDateTime dueAt, LocalDateTime returnedAt) {
        return computeFine(dueAt, returnedAt, resolveFinePerDay(book));
    }

    public BigDecimal computeFine(LocalDateTime dueAt, LocalDateTime returnedAt, BigDecimal finePerDay) {
        if (returnedAt == null || !returnedAt.isAfter(dueAt)) {
            return BigDecimal.ZERO;
        }

        long overdueDays = ChronoUnit.DAYS.between(dueAt.toLocalDate(), returnedAt.toLocalDate());
        if (overdueDays <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = finePerDay != null ? finePerDay : properties.getFinePerDay();
        return rate.multiply(BigDecimal.valueOf(overdueDays))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
