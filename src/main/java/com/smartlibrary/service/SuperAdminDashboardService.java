package com.smartlibrary.service;

import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class SuperAdminDashboardService {

    private final BookRepository bookRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final BookIssueRepository bookIssueRepository;

    public SuperAdminDashboardService(
            BookRepository bookRepository,
            StudentProfileRepository studentProfileRepository,
            BookIssueRepository bookIssueRepository) {
        this.bookRepository = bookRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.bookIssueRepository = bookIssueRepository;
    }

    public Map<String, Object> getCombinedDashboard() {
        Map<String, Object> library;
        try {
            library = getLibraryStats();
        } catch (Exception ex) {
            library = emptyLibraryStats();
        }

        Map<String, Object> model = new HashMap<>();
        model.put("library", library);
        model.put("libBookCount", toLong(library.get("bookCount")));
        model.put("libStudentCount", toLong(library.get("studentCount")));
        model.put("libActiveLoans", toLong(library.get("activeLoans")));
        model.put("libOverdueLoans", toLong(library.get("overdueLoans")));
        return model;
    }

    private Map<String, Object> emptyLibraryStats() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("available", false);
        fallback.put("bookCount", 0L);
        fallback.put("studentCount", 0L);
        fallback.put("activeLoans", 0L);
        fallback.put("overdueLoans", 0L);
        return fallback;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public Map<String, Object> getLibraryStatsForApi() {
        Map<String, Object> stats = new HashMap<>(getLibraryStats());
        stats.put("system", "library");
        stats.put("available", true);
        return stats;
    }

    private Map<String, Object> getLibraryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("available", true);
        stats.put("bookCount", bookRepository.count());
        stats.put("studentCount", studentProfileRepository.count());
        stats.put("activeLoans", bookIssueRepository.countByStatus(IssueStatus.BORROWED));
        stats.put("overdueLoans", bookIssueRepository.countByStatus(IssueStatus.OVERDUE));
        return stats;
    }
}
