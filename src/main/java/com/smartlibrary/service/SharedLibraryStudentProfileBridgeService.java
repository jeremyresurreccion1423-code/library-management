package com.smartlibrary.service;

import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
    private final UserRepository userRepository;
    private final StudentIdService studentIdService;

    public SharedLibraryStudentProfileBridgeService(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            StudentIdService studentIdService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.studentIdService = studentIdService;
    }

    /**
     * Runs in its own read-write transaction so callers marked {@code readOnly = true}
     * (e.g. student dashboard) can still create or repair profiles.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StudentProfile> ensureLibraryStudentProfile(User user) {
        if (user == null) {
            log.warn("ensureLibraryStudentProfile: user is null — returning empty");
            log.info("[TEMP] ensureLibraryStudentProfile: returning Optional.empty() — branch=user is null");
            return Optional.empty();
        }
        if (user.getId() == null) {
            log.warn("ensureLibraryStudentProfile: user id is null for username={} email={} — returning empty",
                    user.getUsername(), user.getEmail());
            log.info("[TEMP] ensureLibraryStudentProfile: returning Optional.empty() — branch=user.getId() is null");
            return Optional.empty();
        }

        log.info("[TEMP] ensureLibraryStudentProfile: user.getId()={}, user.getUsername()={}, user.getEmail()={}",
                user.getId(), user.getUsername(), user.getEmail());

        Optional<StudentProfile> existing = findProfileRecord(user);
        if (existing.isPresent()) {
            repairUserLinkIfNeeded(existing.get(), user);
            Optional<StudentProfile> reloaded = loadProfileById(existing.get().getId());
            if (reloaded.isPresent()) {
                log.info("[TEMP] ensureLibraryStudentProfile: returning Optional.of(profile) profileId={} — branch=findProfileRecord reload succeeded",
                        reloaded.get().getId());
                return reloaded;
            }
            log.error(
                    "ensureLibraryStudentProfile: JDBC found profile id={} for userId={} username={} but reload failed",
                    existing.get().getId(), user.getId(), user.getUsername());
        }

        Optional<StudentProfile> fromAttendance = provisionFromAttendanceStudent(user);
        if (fromAttendance.isPresent()) {
            repairUserLinkIfNeeded(fromAttendance.get(), user);
            Optional<StudentProfile> reloaded = loadProfileById(fromAttendance.get().getId());
            if (reloaded.isPresent()) {
                log.info("ensureLibraryStudentProfile: provisioned from public.students for username={}", user.getUsername());
                log.info("[TEMP] ensureLibraryStudentProfile: returning Optional.of(profile) profileId={} — branch=provisionFromAttendanceStudent reload succeeded",
                        reloaded.get().getId());
                return reloaded;
            }
            log.error(
                    "ensureLibraryStudentProfile: provisionFromAttendanceStudent returned id={} for username={} but reload failed",
                    fromAttendance.get().getId(), user.getUsername());
        }

        Optional<StudentProfile> fromUser = provisionFromSharedUserAccount(user);
        if (fromUser.isPresent()) {
            log.info("ensureLibraryStudentProfile: provisioned from shared user account for username={}", user.getUsername());
            log.info("[TEMP] ensureLibraryStudentProfile: returning Optional.of(profile) profileId={} — branch=provisionFromSharedUserAccount succeeded",
                    fromUser.get().getId());
            return fromUser;
        }

        log.warn(
                "ensureLibraryStudentProfile: returning empty — no library.student_profiles for userId={} username={} email={} role={}. "
                        + "findProfileRecord={}, provisionFromAttendanceStudent={}, provisionFromSharedUserAccount={}",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                existing.isPresent() ? "found-but-unloadable" : "miss",
                fromAttendance.isPresent() ? "returned-but-unloadable" : "miss",
                "miss");
        log.info("[TEMP] ensureLibraryStudentProfile: returning Optional.empty() — branch=final fallback (all paths failed)");
        return Optional.empty();
    }

    private Optional<StudentProfile> findProfileRecord(User user) {
        String email = stringValue(user.getEmail());
        String username = stringValue(user.getUsername());

        Optional<Long> profileId = findProfileId(
                "library.student_profiles by user_id",
                "SELECT id FROM library.student_profiles WHERE user_id = ? LIMIT 1",
                user.getId());
        if (profileId.isPresent()) {
            log.info("[TEMP] findProfileRecord Query1 (by user_id): profile id FOUND={}", profileId.get());
        } else {
            log.info("[TEMP] findProfileRecord Query1 (by user_id): profile id NOT FOUND for user_id={}", user.getId());
        }
        if (profileId.isPresent()) {
            return loadProfileById(profileId.get());
        }

        Optional<StudentProfile> byAttendanceStudentNumber = findProfileByAttendanceStudentNumber(user);
        if (byAttendanceStudentNumber.isPresent()) {
            return byAttendanceStudentNumber;
        }

        if (!username.isBlank()) {
            profileId = findProfileId(
                    "library.student_profiles by joined username",
                    """
                            SELECT sp.id
                            FROM library.student_profiles sp
                            INNER JOIN public.users u ON u.id = sp.user_id
                            WHERE LOWER(u.username) = LOWER(?)
                            LIMIT 1
                            """,
                    username);
            if (profileId.isPresent()) {
                return loadProfileById(profileId.get());
            }
        }

        if (!email.isBlank()) {
            profileId = findProfileId(
                    "library.student_profiles by joined email",
                    """
                            SELECT sp.id
                            FROM library.student_profiles sp
                            INNER JOIN public.users u ON u.id = sp.user_id
                            WHERE LOWER(u.email) = LOWER(?)
                            LIMIT 1
                            """,
                    email);
            if (profileId.isPresent()) {
                return loadProfileById(profileId.get());
            }
        }

        profileId = findProfileId(
                "library.student_profiles by public.students.user_id -> student_number",
                """
                        SELECT sp.id
                        FROM library.student_profiles sp
                        INNER JOIN public.students s ON LOWER(s.student_number) = LOWER(sp.student_id)
                        WHERE s.user_id = ?
                        LIMIT 1
                        """,
                user.getId());
        if (profileId.isPresent()) {
            return loadProfileById(profileId.get());
        }

        if (!email.isBlank()) {
            profileId = findProfileId(
                    "library.student_profiles by public.students.email -> student_number",
                    """
                            SELECT sp.id
                            FROM library.student_profiles sp
                            INNER JOIN public.students s ON LOWER(s.student_number) = LOWER(sp.student_id)
                            WHERE LOWER(s.email) = LOWER(?)
                            LIMIT 1
                            """,
                    email);
            if (profileId.isPresent()) {
                return loadProfileById(profileId.get());
            }
        }

        profileId = findProfileId(
                "library.student_profiles by student_id from public.students (orphan repair)",
                """
                        SELECT sp.id
                        FROM library.student_profiles sp
                        WHERE LOWER(sp.student_id) = (
                            SELECT LOWER(s.student_number)
                            FROM public.students s
                            WHERE s.user_id = ?
                            LIMIT 1
                        )
                        LIMIT 1
                        """,
                user.getId());
        if (profileId.isPresent()) {
            Optional<StudentProfile> profile = loadProfileById(profileId.get());
            profile.ifPresent(value -> repairUserLinkIfNeeded(value, user));
            if (profile.isPresent()) {
                log.info("[TEMP] findProfileRecord: returning Optional.of(profile) profileId={} — branch=orphan repair query",
                        profile.get().getId());
            }
            return profile;
        }

        log.debug(
                "findProfileRecord: no library.student_profiles match for userId={} username={} email={}",
                user.getId(), username, email);
        log.info("[TEMP] findProfileRecord: returning Optional.empty() — branch=no query matched");
        return Optional.empty();
    }

    private Optional<StudentProfile> findProfileByAttendanceStudentNumber(User user) {
        try {
            List<String> studentNumbers = jdbcTemplate.queryForList(
                    "SELECT student_number FROM public.students WHERE user_id = ? LIMIT 1",
                    String.class,
                    user.getId());
            if (studentNumbers.isEmpty()) {
                return Optional.empty();
            }
            String studentNumber = stringValue(studentNumbers.get(0));
            if (studentNumber.isBlank()) {
                return Optional.empty();
            }
            return resolveExistingProfileByStudentId(studentNumber, user);
        } catch (DataAccessException ex) {
            log.error(
                    "findProfileByAttendanceStudentNumber: lookup failed for userId={} username={}: {}",
                    user.getId(), user.getUsername(), ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    private Optional<StudentProfile> resolveExistingProfileByStudentId(String studentId, User user) {
        Optional<StudentProfile> profile = loadProfileByStudentId(studentId);
        if (profile.isEmpty()) {
            profile = findProfileId(
                    "library.student_profiles by student_id exact",
                    "SELECT id FROM library.student_profiles WHERE student_id = ? LIMIT 1",
                    stringValue(studentId))
                    .flatMap(this::loadProfileById);
        }
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        repairUserLinkIfNeeded(profile.get(), user);
        return loadProfileById(profile.get().getId());
    }

    private Optional<Long> findProfileId(String queryLabel, String sql, Object... args) {
        try {
            List<Long> profileIds = jdbcTemplate.queryForList(sql, Long.class, args);
            if (profileIds.isEmpty()) {
                log.debug("findProfileRecord: {} returned no rows", queryLabel);
                log.info("[TEMP] findProfileId: returning Optional.empty() — branch={} returned no rows", queryLabel);
                return Optional.empty();
            }
            log.debug("findProfileRecord: {} returned profile id={}", queryLabel, profileIds.get(0));
            return Optional.of(profileIds.get(0));
        } catch (DataAccessException ex) {
            log.error("findProfileRecord: {} failed for args={}: {}", queryLabel, args, ex.getMessage(), ex);
            log.info("[TEMP] findProfileId: returning Optional.empty() — branch={} DataAccessException", queryLabel);
            return Optional.empty();
        }
    }

    private Optional<StudentProfile> loadProfileById(Long profileId) {
        if (profileId == null) {
            log.info("[TEMP] loadProfileById: returning Optional.empty() — branch=profileId is null");
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, student_id, full_name, phone, course, user_id, version, created_at, updated_at
                    FROM library.student_profiles
                    WHERE id = ?
                    """, profileId);
            if (rows.isEmpty()) {
                log.warn("loadProfileById: no row in library.student_profiles for id={}", profileId);
                log.info("[TEMP] loadProfileById: returning Optional.empty() — branch=JDBC query returned no rows for id={}", profileId);
                return Optional.empty();
            }
            StudentProfile profile = mapRowToProfile(rows.get(0));
            log.info("[TEMP] loadProfileById: returning Optional.of(profile) profileId={}", profile.getId());
            return Optional.of(profile);
        } catch (DataAccessException ex) {
            log.error("loadProfileById: JDBC load failed for id={}: {}", profileId, ex.getMessage(), ex);
            log.info("[TEMP] loadProfileById: returning Optional.empty() — branch=DataAccessException for id={}", profileId);
            return Optional.empty();
        }
    }

    private Optional<StudentProfile> loadProfileByStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            log.info("[TEMP] loadProfileByStudentId: returning Optional.empty() — branch=studentId is null or blank");
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT id, student_id, full_name, phone, course, user_id, version, created_at, updated_at
                    FROM library.student_profiles
                    WHERE LOWER(student_id) = LOWER(?)
                    LIMIT 1
                    """, studentId);
            if (rows.isEmpty()) {
                log.debug("loadProfileByStudentId: no row for studentId={}", studentId);
                log.info("[TEMP] loadProfileByStudentId: returning Optional.empty() — branch=JDBC query returned no rows for studentId={}", studentId);
                return Optional.empty();
            }
            StudentProfile profile = mapRowToProfile(rows.get(0));
            log.info("[TEMP] loadProfileByStudentId: returning Optional.of(profile) profileId={}", profile.getId());
            return Optional.of(profile);
        } catch (DataAccessException ex) {
            log.error("loadProfileByStudentId: JDBC load failed for studentId={}: {}", studentId, ex.getMessage(), ex);
            log.info("[TEMP] loadProfileByStudentId: returning Optional.empty() — branch=DataAccessException for studentId={}", studentId);
            return Optional.empty();
        }
    }

    private StudentProfile mapRowToProfile(Map<String, Object> row) {
        StudentProfile profile = new StudentProfile();
        profile.setId(((Number) row.get("id")).longValue());

        Object version = row.get("version");
        if (version instanceof Number number) {
            profile.setVersion(number.longValue());
        }

        profile.setStudentId(stringValue(row.get("student_id")));
        profile.setFullName(stringValue(row.get("full_name")));
        profile.setPhone(stringValue(row.get("phone")));
        profile.setCourse(stringValue(row.get("course")));

        Object createdAt = row.get("created_at");
        if (createdAt instanceof Timestamp timestamp) {
            profile.setCreatedAt(timestamp.toLocalDateTime());
        } else if (createdAt instanceof LocalDateTime localDateTime) {
            profile.setCreatedAt(localDateTime);
        }

        Object updatedAt = row.get("updated_at");
        if (updatedAt instanceof Timestamp timestamp) {
            profile.setUpdatedAt(timestamp.toLocalDateTime());
        } else if (updatedAt instanceof LocalDateTime localDateTime) {
            profile.setUpdatedAt(localDateTime);
        }

        Object userIdValue = row.get("user_id");
        if (userIdValue instanceof Number userIdNumber) {
            userRepository.findById(userIdNumber.longValue()).ifPresent(profile::setUser);
        }

        return profile;
    }

    private void repairUserLinkIfNeeded(StudentProfile profile, User user) {
        if (profile == null || profile.getId() == null || user == null || user.getId() == null) {
            return;
        }

        Long linkedUserId;
        try {
            linkedUserId = jdbcTemplate.queryForObject(
                    "SELECT user_id FROM library.student_profiles WHERE id = ?",
                    Long.class,
                    profile.getId());
        } catch (DataAccessException ex) {
            log.error(
                    "repairUserLinkIfNeeded: failed to read user_id for profileId={} username={}: {}",
                    profile.getId(), user.getUsername(), ex.getMessage(), ex);
            return;
        }

        if (user.getId().equals(linkedUserId)) {
            return;
        }

        try {
            jdbcTemplate.update(
                    "UPDATE library.student_profiles SET user_id = ?, updated_at = NOW() WHERE id = ?",
                    user.getId(), profile.getId());
            log.info(
                    "Repaired library.student_profiles.user_id for profile studentId={} profileId={}: {} -> user {} (id={})",
                    profile.getStudentId(),
                    profile.getId(),
                    linkedUserId,
                    user.getUsername(),
                    user.getId());
            userRepository.findById(user.getId()).ifPresent(profile::setUser);
        } catch (DataAccessException ex) {
            log.error(
                    "repairUserLinkIfNeeded: UPDATE failed for profileId={} username={} targetUserId={}: {}",
                    profile.getId(), user.getUsername(), user.getId(), ex.getMessage(), ex);
        }
    }

    private Optional<StudentProfile> provisionFromAttendanceStudent(User user) {
        List<Map<String, Object>> attendanceStudents;
        try {
            attendanceStudents = jdbcTemplate.queryForList("""
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
        } catch (DataAccessException ex) {
            log.error(
                    "provisionFromAttendanceStudent: public.students lookup failed for userId={} username={} email={}: {}",
                    user.getId(), user.getUsername(), user.getEmail(), ex.getMessage(), ex);
            log.info("[TEMP] provisionFromAttendanceStudent: returning Optional.empty() — branch=public.students lookup DataAccessException");
            return Optional.empty();
        }

        if (attendanceStudents.isEmpty()) {
            log.debug(
                    "provisionFromAttendanceStudent: no public.students row for userId={} username={} email={}",
                    user.getId(), user.getUsername(), user.getEmail());
            log.info("[TEMP] provisionFromAttendanceStudent: returning Optional.empty() — branch=no public.students row");
            return Optional.empty();
        }

        Map<String, Object> row = attendanceStudents.get(0);
        String studentId = stringValue(row.get("student_number"));
        if (studentId.isBlank()) {
            studentId = studentIdService.generateNextStudentId();
            log.warn(
                    "provisionFromAttendanceStudent: public.students row had blank student_number for userId={} username={}, generated {}",
                    user.getId(), user.getUsername(), studentId);
        } else {
            log.debug(
                    "provisionFromAttendanceStudent: found public.students row student_number={} for userId={} username={}",
                    studentId, user.getId(), user.getUsername());
        }

        Optional<StudentProfile> existingByStudentId = resolveExistingProfileByStudentId(studentId, user);
        if (existingByStudentId.isPresent()) {
            log.info(
                    "provisionFromAttendanceStudent: reusing existing library.student_profiles id={} studentId={} for username={}",
                    existingByStudentId.get().getId(), studentId, user.getUsername());
            return existingByStudentId;
        }

        log.info(
                "provisionFromAttendanceStudent: inserting library.student_profiles for userId={} username={} studentId={}",
                user.getId(), user.getUsername(), studentId);
        return saveNewProfile(
                user,
                studentId,
                stringValue(row.get("full_name")),
                stringValue(row.get("contact_number")),
                stringValue(row.get("department_name")),
                "Auto-provisioned library student profile {} for shared user {}");
    }

    private Optional<StudentProfile> provisionFromSharedUserAccount(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            log.debug(
                    "provisionFromSharedUserAccount: skipping userId={} username={} — role is {} not STUDENT",
                    user.getId(), user.getUsername(), user.getRole());
            log.info("[TEMP] provisionFromSharedUserAccount: returning Optional.empty() — branch=role is not STUDENT (role={})", user.getRole());
            return Optional.empty();
        }

        String fullName = resolveFullName(user);
        if (fullName.isBlank()) {
            log.warn(
                    "provisionFromSharedUserAccount: blank full name for userId={} username={} email={}",
                    user.getId(), user.getUsername(), user.getEmail());
            log.info("[TEMP] provisionFromSharedUserAccount: returning Optional.empty() — branch=blank full name");
            return Optional.empty();
        }

        String studentId = studentIdService.generateNextStudentId();
        log.info(
                "provisionFromSharedUserAccount: inserting library.student_profiles for userId={} username={} generatedStudentId={}",
                user.getId(), user.getUsername(), studentId);
        return saveNewProfile(user, studentId, fullName, "", "",
                "Created library student profile {} for shared account {}");
    }

    private Optional<StudentProfile> saveNewProfile(
            User user, String studentId, String fullName, String phone, String course, String logTemplate) {
        Long userId = user.getId();
        Integer userExists;
        try {
            userExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.users WHERE id = ?",
                    Integer.class,
                    userId);
        } catch (DataAccessException ex) {
            log.error(
                    "saveNewProfile: public.users existence check failed for userId={} username={}: {}",
                    userId, user.getUsername(), ex.getMessage(), ex);
            log.info("[TEMP] saveNewProfile: returning Optional.empty() — branch=public.users existence check DataAccessException");
            return Optional.empty();
        }

        if (userExists == null || userExists == 0) {
            log.error(
                    "saveNewProfile: cannot insert — userId={} username={} not found in public.users",
                    userId, user.getUsername());
            log.info("[TEMP] saveNewProfile: returning Optional.empty() — branch=user not found in public.users");
            return Optional.empty();
        }

        String resolvedName = fullName.isBlank() ? user.getUsername() : fullName;
        try {
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
            Optional<StudentProfile> loaded = loadProfileById(profileId);
            if (loaded.isEmpty()) {
                log.error(
                        "saveNewProfile: INSERT succeeded (id={}) but JDBC reload failed for userId={} username={} studentId={}",
                        profileId, userId, user.getUsername(), studentId);
                log.info("[TEMP] saveNewProfile: returning Optional.empty() — branch=INSERT succeeded but reload failed (insertedId={})",
                        profileId);
            } else {
                log.info("[TEMP] saveNewProfile: returning Optional.of(profile) profileId={} — branch=INSERT succeeded",
                        loaded.get().getId());
            }
            return loaded;
        } catch (DuplicateKeyException ex) {
            log.warn(
                    "saveNewProfile: duplicate student_id={} for username={} — loading existing profile instead of failing",
                    studentId, user.getUsername());
            Optional<StudentProfile> existing = resolveExistingProfileByStudentId(studentId, user);
            if (existing.isPresent()) {
                log.info(
                        "saveNewProfile: recovered existing library.student_profiles id={} studentId={} for username={}",
                        existing.get().getId(), studentId, user.getUsername());
                log.info("[TEMP] saveNewProfile: returning Optional.of(profile) profileId={} — branch=duplicate student_id recovery",
                        existing.get().getId());
                return existing;
            }
            log.error(
                    "saveNewProfile: duplicate student_id={} but could not load existing row for username={}",
                    studentId, user.getUsername(), ex);
            log.info("[TEMP] saveNewProfile: returning Optional.empty() — branch=duplicate student_id but reload failed");
            return Optional.empty();
        } catch (DataAccessException ex) {
            log.error(
                    "saveNewProfile: INSERT into library.student_profiles failed for userId={} username={} email={} studentId={}: {}",
                    userId, user.getUsername(), user.getEmail(), studentId, ex.getMessage(), ex);
            log.info("[TEMP] saveNewProfile: returning Optional.empty() — branch=INSERT DataAccessException");
            return Optional.empty();
        }
    }

    private String resolveFullName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }

        List<String> names;
        try {
            names = jdbcTemplate.queryForList("""
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
        } catch (DataAccessException ex) {
            log.error(
                    "resolveFullName: public.students lookup failed for userId={} username={}: {}",
                    user.getId(), user.getUsername(), ex.getMessage(), ex);
            return "";
        }

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
