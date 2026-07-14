package com.smartlibrary.service;

import com.smartlibrary.repository.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Allocates Library {@code student_id} values that do not collide with existing
 * Library or Attendance student numbers.
 */
@Service
public class StudentIdService {

    private static final Logger logger = LoggerFactory.getLogger(StudentIdService.class);
    private static final String INSTITUTION_CODE = "101";
    private static final String ID_FORMAT = "%s-%03d";

    private final StudentProfileRepository studentProfileRepository;
    private final JdbcTemplate jdbcTemplate;

    public StudentIdService(StudentProfileRepository studentProfileRepository, JdbcTemplate jdbcTemplate) {
        this.studentProfileRepository = studentProfileRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String generateNextStudentId() {
        int next = Math.max(nextSequenceFromLibrary(), nextSequenceFromAttendance()) + 1;
        for (int i = 0; i < 5000; i++) {
            String candidate = String.format(ID_FORMAT, INSTITUTION_CODE, next + i);
            if (!studentIdTaken(candidate)) {
                logger.debug("Generated student ID: {}", candidate);
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique student ID. Please try again.");
    }

    private int nextSequenceFromLibrary() {
        Integer max = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(student_id FROM '([0-9]+)$') AS INTEGER)), -1)
                FROM library.student_profiles
                WHERE student_id ~ '^101-[0-9]+$'
                """, Integer.class);
        return max == null ? -1 : max;
    }

    private int nextSequenceFromAttendance() {
        try {
            Integer max = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(MAX(CAST(SUBSTRING(student_number FROM '([0-9]+)$') AS INTEGER)), -1)
                    FROM public.students
                    WHERE student_number ~ '^101-[0-9]+$'
                    """, Integer.class);
            return max == null ? -1 : max;
        } catch (Exception ex) {
            logger.debug("Could not read Attendance student numbers for ID allocation: {}", ex.getMessage());
            return -1;
        }
    }

    private boolean studentIdTaken(String studentId) {
        if (studentProfileRepository.findByStudentId(studentId).isPresent()) {
            return true;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.students WHERE LOWER(student_number) = LOWER(?)",
                    Integer.class,
                    studentId);
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
