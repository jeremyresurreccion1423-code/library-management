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
import org.springframework.web.client.RestClientException;
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
        Map<String, Object> model = new HashMap<>();
        model.put("attendance", fetchAttendanceStats());
        model.put("library", getLibraryStats());
        model.put("attendanceAvailable", model.get("attendance") instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("available")));
        return model;
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
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("available", false);
        fallback.put("totalStudents", 0);
        fallback.put("totalSubjects", 0);
        fallback.put("todayAttendance", 0);
        fallback.put("lowAttendanceCount", 0);

        try {
            String base = libraryProperties.getAttendanceAppUrl();
            if (base == null || base.isBlank()) {
                return fallback;
            }
            while (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
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
            return body;
        } catch (RestClientException ex) {
            return fallback;
        }
    }
}
