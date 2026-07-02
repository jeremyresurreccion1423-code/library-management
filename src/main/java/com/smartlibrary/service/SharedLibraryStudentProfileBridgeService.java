package com.smartlibrary.service;

import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@code library.student_profiles} for accounts in shared {@code public.users}.
 * Uses JDBC for cross-schema lookups, repairs broken user_id links after Phase 3 migration,
 * and auto-provisions missing profiles for student accounts.
 */
@Service
public class SharedLibraryStudentProfileBridgeService {

    private static final Logger log = LoggerFactory.getLogger(SharedLibraryStudentProfileBridgeService.class);

    private final JdbcTemplate jdbcTemplate;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final StudentIdService studentIdService;

    public SharedLibraryStudentProfileBridgeService(
            JdbcTemplate jdbcTemplate,
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            StudentIdService studentIdService) {
        this.jdbcTemplate = jdbcTemplate;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.studentIdService = studentIdService;
    }

    /**
     * Runs in its own read-write transaction so callers marked {@code readOnly = true}
     * (e.g. student dashboard) can still create or repair profiles.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StudentProfile> ensureLibraryStudentProfile(User user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }

        Optional<StudentProfile> existing = findProfileRecord(user);
        if (existing.isPresent()) {
            repairUserLinkIfNeeded(existing.get(), user);
            return studentProfileRepository.findById(existing.get().getId());
        }

        try {
            Optional<StudentProfile> fromAttendance = provisionFromAttendanceStudent(user);
            if (fromAttendance.isPresent()) {
                repairUserLinkIfNeeded(fromAttendance.get(), user);
                return studentProfileRepository.findById(fromAttendance.get().getId());
            }

            Optional<StudentProfile> fromUser = provisionFromSharedUserAccount(user);
            if (fromUser.isPresent()) {
                return fromUser;
            }

            log.warn("Unable to resolve library.student_profiles for user id={}, username={}, email={}",
                    user.getId(), user.getUsername(), user.getEmail());
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Failed to provision library student profile for user {}: {}", user.getUsername(), ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private Optional<StudentProfile> findProfileRecord(User user) {
        String email = stringValue(user.getEmail());
        String username = stringValue(user.getUsername());

        Optional<Long> profileId = findProfileId(
                "SELECT id FROM library.student_profiles WHERE user_id = ? LIMIT 1",
                user.getId());
        if (profileId.isPresent()) {
            return studentProfileRepository.findById(profileId.get());
        }

        if (!username.isBlank()) {
            profileId = findProfileId("""
                    SELECT sp.id
                    FROM library.student_profiles sp
                    INNER JOIN public.users u ON u.id = sp.user_id
                    WHERE LOWER(u.username) = LOWER(?)
                    LIMIT 1
                    """, username);
            if (profileId.isPresent()) {
                return studentProfileRepository.findById(profileId.get());
            }
        }

        if (!email.isBlank()) {
            profileId = findProfileId("""
                    SELECT sp.id
                    FROM library.student_profiles sp
                    INNER JOIN public.users u ON u.id = sp.user_id
                    WHERE LOWER(u.email) = LOWER(?)
                    LIMIT 1
                    """, email);
            if (profileId.isPresent()) {
                return studentProfileRepository.findById(profileId.get());
            }
        }

        profileId = findProfileId("""
                SELECT sp.id
                FROM library.student_profiles sp
                INNER JOIN public.students s ON LOWER(s.student_number) = LOWER(sp.student_id)
                WHERE s.user_id = ?
                LIMIT 1
                """, user.getId());
        if (profileId.isPresent()) {
            return studentProfileRepository.findById(profileId.get());
        }

        if (!email.isBlank()) {
            profileId = findProfileId("""
                    SELECT sp.id
                    FROM library.student_profiles sp
                    INNER JOIN public.students s ON LOWER(s.student_number) = LOWER(sp.student_id)
                    WHERE LOWER(s.email) = LOWER(?)
                    LIMIT 1
                    """, email);
            if (profileId.isPresent()) {
                return studentProfileRepository.findById(profileId.get());
            }
        }

        return Optional.empty();
    }

    private Optional<Long> findProfileId(String sql, Object... args) {
        List<Long> profileIds = jdbcTemplate.queryForList(sql, Long.class, args);
        if (profileIds.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(profileIds.get(0));
    }

    private void repairUserLinkIfNeeded(StudentProfile profile, User user) {
        Long linkedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM library.student_profiles WHERE id = ?",
                Long.class,
                profile.getId());
        if (user.getId().equals(linkedUserId)) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE library.student_profiles SET user_id = ? WHERE id = ?",
                user.getId(), profile.getId());
        log.info("Repaired library.student_profiles.user_id for profile {} -> user {}", profile.getStudentId(), user.getUsername());
    }

    private Optional<StudentProfile> provisionFromAttendanceStudent(User user) {
        List<Map<String, Object>> attendanceStudents = jdbcTemplate.queryForList("""
                SELECT s.student_number, s.full_name, s.contact_number, s.email,
                       COALESCE(d.name, 'General') AS department_name
                FROM public.students s
                LEFT JOIN public.departments d ON d.id = s.department_id
                WHERE s.user_id = ?
                   OR (? <> '' AND s.email IS NOT NULL AND ? <> '' AND LOWER(s.email) = LOWER(?))
                ORDER BY CASE WHEN s.user_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """,
                user.getId(),
                stringValue(user.getEmail()), stringValue(user.getEmail()), stringValue(user.getEmail()),
                user.getId());

        if (attendanceStudents.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> row = attendanceStudents.get(0);
        String studentId = stringValue(row.get("student_number"));
        if (studentId.isBlank()) {
            studentId = studentIdService.generateNextStudentId();
        }

        Optional<StudentProfile> existingByStudentId = studentProfileRepository.findByStudentId(studentId);
        if (existingByStudentId.isPresent()) {
            return existingByStudentId;
        }

        return saveNewProfile(user, studentId, stringValue(row.get("full_name")),
                stringValue(row.get("contact_number")), stringValue(row.get("department_name")),
                "Auto-provisioned library student profile {} for shared user {}");
    }

    private Optional<StudentProfile> provisionFromSharedUserAccount(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            return Optional.empty();
        }

        String fullName = resolveFullName(user);
        if (fullName.isBlank()) {
            return Optional.empty();
        }

        String studentId = studentIdService.generateNextStudentId();
        return saveNewProfile(user, studentId, fullName, "", "",
                "Created library student profile {} for shared account {}");
    }

    private Optional<StudentProfile> saveNewProfile(
            User user, String studentId, String fullName, String phone, String course, String logTemplate) {
        Long userId = user.getId();
        Integer userExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.users WHERE id = ?",
                Integer.class,
                userId);
        if (userExists == null || userExists == 0) {
            log.error("Cannot create library profile: user id {} not found in public.users", userId);
            return Optional.empty();
        }

        String resolvedName = fullName.isBlank() ? user.getUsername() : fullName;
        Long profileId = jdbcTemplate.queryForObject("""
                INSERT INTO library.student_profiles
                    (student_id, full_name, phone, course, user_id, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, NOW(), NOW())
                RETURNING id
                """,
                Long.class,
                studentId,
                resolvedName,
                phone == null ? "" : phone,
                course == null ? "" : course,
                userId);

        log.info(logTemplate, studentId, user.getUsername());
        return studentProfileRepository.findById(profileId);
    }

    private String resolveFullName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }

        List<String> names = jdbcTemplate.queryForList("""
                SELECT full_name
                FROM public.students
                WHERE user_id = ?
                   OR (? <> '' AND email IS NOT NULL AND ? <> '' AND LOWER(email) = LOWER(?))
                ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """,
                String.class,
                user.getId(),
                stringValue(user.getEmail()), stringValue(user.getEmail()), stringValue(user.getEmail()),
                user.getId());

        if (!names.isEmpty() && names.get(0) != null && !names.get(0).isBlank()) {
            return names.get(0).trim();
        }

        String username = stringValue(user.getUsername());
        if (username.isBlank()) {
            return "";
        }
        return username.substring(0, 1).toUpperCase() + username.substring(1) + " Student";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
