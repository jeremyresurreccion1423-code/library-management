package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.BookRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class SuperAdminDashboardService {

    private final BookRepository bookRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final BookIssueRepository bookIssueRepository;
    private final RestTemplate restTemplate;
    private final LibraryProperties libraryProperties;
    private final String ssoSecret;

    public SuperAdminDashboardService(
            BookRepository bookRepository,
            StudentProfileRepository studentProfileRepository,
            BookIssueRepository bookIssueRepository,
            RestTemplate restTemplate,
            LibraryProperties libraryProperties,
            @Value("${super-admin.sso-secret}") String ssoSecret) {
        this.bookRepository = bookRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.bookIssueRepository = bookIssueRepository;
        this.restTemplate = restTemplate;
        this.libraryProperties = libraryProperties;
        this.ssoSecret = ssoSecret;
    }

    public Map<String, Object> getCombinedDashboard() {
        Map<String, Object> attendance;
        Map<String, Object> library;
        try {
            attendance = fetchAttendanceStats();
        } catch (Exception ex) {
            attendance = emptyAttendanceStats();
        }
        try {
            library = getLibraryStats();
        } catch (Exception ex) {
            library = emptyLibraryStats();
        }

        Map<String, Object> model = new HashMap<>();
        model.put("attendance", attendance);
        model.put("library", library);
        model.put("attendanceAvailable", Boolean.TRUE.equals(attendance.get("available")));

        // Flat attributes avoid nested Map SpEL issues in Thymeleaf on Railway
        model.put("attTotalStudents", toLong(attendance.get("totalStudents")));
        model.put("attTotalSubjects", toLong(attendance.get("totalSubjects")));
        model.put("attTodayAttendance", toLong(attendance.get("todayAttendance")));
        model.put("attLowAttendanceCount", toLong(attendance.get("lowAttendanceCount")));
        model.put("libBookCount", toLong(library.get("bookCount")));
        model.put("libStudentCount", toLong(library.get("studentCount")));
        model.put("libActiveLoans", toLong(library.get("activeLoans")));
        model.put("libOverdueLoans", toLong(library.get("overdueLoans")));
        return model;
    }

    private Map<String, Object> emptyAttendanceStats() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("available", false);
        fallback.put("totalStudents", 0L);
        fallback.put("totalSubjects", 0L);
        fallback.put("todayAttendance", 0L);
        fallback.put("lowAttendanceCount", 0L);
        return fallback;
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

    private Map<String, Object> fetchAttendanceStats() {
        Map<String, Object> fallback = emptyAttendanceStats();

        try {
            String base = normalizeBaseUrl(libraryProperties.getAttendanceAppUrl());
            if (base == null) {
                return fallback;
            }
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Super-Admin-Secret", ssoSecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    base + "/api/v1/super-admin/dashboard-stats",
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {});
            if (response.getBody() == null) {
                return fallback;
            }
            Map<String, Object> body = new HashMap<>(response.getBody());
            body.put("available", true);
            body.putIfAbsent("totalStudents", 0);
            body.putIfAbsent("totalSubjects", 0);
            body.putIfAbsent("todayAttendance", 0);
            body.putIfAbsent("lowAttendanceCount", 0);
            return body;
        } catch (Exception ex) {
            return fallback;
        }
    }

    /** Accepts host-only Railway values and makes them absolute https URLs. */
    public static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String base = raw.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://" + base;
        }
        return base;
    }
}
