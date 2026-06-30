package com.smartlibrary.service;

import com.smartlibrary.entity.Book;
import com.smartlibrary.entity.BookIssue;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalyticsService {

    private static final String[] WEEKDAY_LABELS =
            {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final int[] MYSQL_DOW_FOR_WEEKDAY = {2, 3, 4, 5, 6, 7, 1};

    private final BookIssueRepository bookIssueRepository;
    private final BookRepository bookRepository;

    public AnalyticsService(BookIssueRepository bookIssueRepository, BookRepository bookRepository) {
        this.bookIssueRepository = bookIssueRepository;
        this.bookRepository = bookRepository;
    }

    public List<Map<String, Object>> mostBorrowedBooks(int limit) {
        List<Object[]> rows = bookIssueRepository.countIssuesByBook();
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        
        for (Object[] row : rows) {
            if (i++ >= limit) {
                break;
            }
            
            Long bookId = (Long) row[0];
            Long cnt = (Long) row[1];
            if (bookId == null) continue;
            
            Book b = bookRepository.findById(bookId).orElse(null);
            out.add(Map.of(
                    "bookId", bookId,
                    "title", b != null ? b.getTitle() : "?",
                    "borrowCount", cnt));
        }
        
        return out;
    }

    public BigDecimal overdueRatePercent() {
        long total = bookIssueRepository.count();
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        
        long overdue = bookIssueRepository.countByStatus(IssueStatus.OVERDUE);
        return BigDecimal.valueOf(overdue * 100.0 / total)
                .setScale(1, RoundingMode.HALF_UP);
    }

    public List<BookIssue> studentReadingHistory(Long studentProfileId) {
        return bookIssueRepository.historyForStudent(studentProfileId);
    }

    public Map<String, Long> issueStatusCounts() {
        return Map.of(
                "borrowed", bookIssueRepository.countByStatus(IssueStatus.BORROWED),
                "returned", bookIssueRepository.countByStatus(IssueStatus.RETURNED),
                "overdue", bookIssueRepository.countByStatus(IssueStatus.OVERDUE));
    }

    public long totalIssues() {
        return bookIssueRepository.count();
    }

    public List<Map<String, Object>> borrowsByDayOfWeek() {
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : bookIssueRepository.countIssuesByDayOfWeek()) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < WEEKDAY_LABELS.length; i++) {
            long count = counts.getOrDefault(MYSQL_DOW_FOR_WEEKDAY[i], 0L);
            result.add(Map.of("day", WEEKDAY_LABELS[i], "count", count));
        }
        return result;
    }

    public Optional<String> busiestDayOfWeek() {
        return borrowsByDayOfWeek().stream()
                .max(Comparator.comparingLong(row -> (Long) row.get("count")))
                .filter(row -> (Long) row.get("count") > 0)
                .map(row -> (String) row.get("day"));
    }

    public List<Map<String, Object>> borrowsByRecentDays(int days) {
        int window = Math.max(days, 1);
        LocalDate start = LocalDate.now().minusDays(window - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();

        for (Object[] row : bookIssueRepository.countIssuesByDateSince(start.atStartOfDay())) {
            LocalDate date = toLocalDate(row[0]);
            if (date == null || row[1] == null) {
                continue;
            }
            counts.put(date, ((Number) row[1]).longValue());
        }

        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM d");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < window; i++) {
            LocalDate date = start.plusDays(i);
            result.add(Map.of(
                    "date", date.toString(),
                    "label", date.format(labelFmt),
                    "count", counts.getOrDefault(date, 0L)));
        }
        return result;
    }

    public Optional<Map<String, Object>> busiestRecentDay(int days) {
        return borrowsByRecentDays(days).stream()
                .max(Comparator.comparingLong(row -> (Long) row.get("count")))
                .filter(row -> (Long) row.get("count") > 0);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }
}
