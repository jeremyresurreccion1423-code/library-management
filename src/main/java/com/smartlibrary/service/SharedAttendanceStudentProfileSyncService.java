package com.smartlibrary.service;

import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Creates {@code public.students} when a student registers in the Library system.
 */
@Service
public class SharedAttendanceStudentProfileSyncService {

    private static final Logger log = LoggerFactory.getLogger(SharedAttendanceStudentProfileSyncService.class);

    private final JdbcTemplate jdbcTemplate;

    public SharedAttendanceStudentProfileSyncService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Provisions Attendance {@code public.students} from a Library registration.
     * Idempotent — skips when a student row already exists for the user or student number.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncFromLibraryRegistration(User user, StudentProfile libraryProfile) {
        if (user == null || user.getId() == null || libraryProfile == null) {
            return;
        }

        if (attendanceStudentExists(user.getId(), libraryProfile.getStudentId())) {
            return;
        }

        String studentNumber = stringValue(libraryProfile.getStudentId());
        if (studentNumber.isBlank()) {
            log.warn("Skipping Attendance sync: Library profile has blank student_id for user {}", user.getUsername());
            return;
        }

        Optional<Long> existingByNumber = findAttendanceStudentIdByNumber(studentNumber);
        if (existingByNumber.isPresent()) {
            linkAttendanceStudentToUser(existingByNumber.get(), user.getId());
            return;
        }

        long departmentId = resolveDepartmentId(stringValue(libraryProfile.getCourse()));
        long sectionId = resolveSectionId(departmentId, stringValue(libraryProfile.getCourse()));

        String email = stringValue(user.getEmail());
        if (!email.isBlank() && attendanceEmailTaken(email)) {
            email = null;
        }

        jdbcTemplate.update("""
                INSERT INTO public.students
                    (user_id, student_number, full_name, email, contact_number,
                     department_id, section_id, year_level, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """,
                user.getId(),
                studentNumber,
                stringValue(libraryProfile.getFullName()),
                email,
                stringValue(libraryProfile.getPhone()),
                departmentId,
                sectionId,
                "1st Year",
                "ACTIVE");

        log.info("Auto-provisioned Attendance student {} for Library user {}", studentNumber, user.getUsername());
    }

    private boolean attendanceStudentExists(Long userId, String studentNumber) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM public.students
                WHERE user_id = ?
                   OR (? <> '' AND LOWER(student_number) = LOWER(?))
                """, Integer.class, userId, stringValue(studentNumber), stringValue(studentNumber));
        return count != null && count > 0;
    }

    private Optional<Long> findAttendanceStudentIdByNumber(String studentNumber) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM public.students WHERE LOWER(student_number) = LOWER(?) LIMIT 1",
                Long.class,
                studentNumber);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    private void linkAttendanceStudentToUser(Long studentId, Long userId) {
        jdbcTemplate.update("UPDATE public.students SET user_id = ? WHERE id = ?", userId, studentId);
        log.info("Linked existing Attendance student id {} to user id {}", studentId, userId);
    }

    private boolean attendanceEmailTaken(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.students WHERE LOWER(email) = LOWER(?)",
                Integer.class,
                email);
        return count != null && count > 0;
    }

    private long resolveDepartmentId(String courseName) {
        String name = courseName.isBlank() ? "General" : courseName;
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM public.departments WHERE LOWER(name) = LOWER(?) LIMIT 1",
                Long.class,
                name);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbcTemplate.queryForObject("""
                INSERT INTO public.departments (name, description, created_at)
                VALUES (?, ?, NOW())
                RETURNING id
                """, Long.class, name, "Synced from Library registration");
    }

    private long resolveSectionId(long departmentId, String courseName) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM public.sections WHERE department_id = ? ORDER BY id LIMIT 1",
                Long.class,
                departmentId);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        String sectionName = (courseName.isBlank() ? "General" : courseName) + "-LIB";
        return jdbcTemplate.queryForObject("""
                INSERT INTO public.sections (name, department_id, year_level, created_at)
                VALUES (?, ?, ?, NOW())
                RETURNING id
                """, Long.class, sectionName, departmentId, "1st Year");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
