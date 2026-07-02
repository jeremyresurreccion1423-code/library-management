package com.smartlibrary.service;

import com.smartlibrary.entity.AdminRevenue;
import com.smartlibrary.entity.BookIssue;
import com.smartlibrary.repository.AdminRevenueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AdminRevenueService {

    private static final Logger logger = LoggerFactory.getLogger(AdminRevenueService.class);
    private static final BigDecimal RETURN_PROCESSING_FEE = new BigDecimal("10.00");

    private final AdminRevenueRepository adminRevenueRepository;

    public AdminRevenueService(AdminRevenueRepository adminRevenueRepository) {
        this.adminRevenueRepository = adminRevenueRepository;
    }

    @Transactional
    public AdminRevenue createRevenueFromBookReturn(BookIssue bookIssue) {
        BigDecimal revenueAmount = calculateReturnRevenue(bookIssue);
        if (revenueAmount.compareTo(BigDecimal.ZERO) > 0) {
            AdminRevenue revenue = new AdminRevenue(bookIssue, revenueAmount, "BOOK_RETURN");
            AdminRevenue saved = adminRevenueRepository.save(revenue);
            logger.info("Revenue recorded from book return: ₱{} for book issue {}", revenueAmount, bookIssue.getId());
            return saved;
        }
        return null;
    }

    @Transactional
    public AdminRevenue createRevenueFromFine(BookIssue bookIssue) {
        if (bookIssue.getFineAmount() != null && bookIssue.getFineAmount().compareTo(BigDecimal.ZERO) > 0) {
            AdminRevenue revenue = new AdminRevenue(bookIssue, bookIssue.getFineAmount(), "FINE");
            AdminRevenue saved = adminRevenueRepository.save(revenue);
            logger.info("Revenue recorded from fine: ₱{} for book issue {}", bookIssue.getFineAmount(), bookIssue.getId());
            return saved;
        }
        return null;
    }

    private BigDecimal calculateReturnRevenue(BookIssue bookIssue) {
        return RETURN_PROCESSING_FEE;
    }
}
