package com.smartlibrary.repository;

import com.smartlibrary.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByStudentId(String studentId);

    @Query("SELECT s FROM StudentProfile s JOIN FETCH s.user WHERE s.studentId = :studentId")
    Optional<StudentProfile> findByStudentIdWithUser(@Param("studentId") String studentId);

    @Query("SELECT s FROM StudentProfile s JOIN FETCH s.user WHERE s.user.username = :username")
    Optional<StudentProfile> findByUserUsername(@Param("username") String username);

    @Query("SELECT s FROM StudentProfile s JOIN FETCH s.user WHERE s.user.id = :userId")
    Optional<StudentProfile> findByUserId(@Param("userId") Long userId);
    
    @Query("SELECT s FROM StudentProfile s JOIN FETCH s.user ORDER BY s.fullName ASC")
    List<StudentProfile> findAllWithUsers();

    @Query("SELECT s FROM StudentProfile s JOIN FETCH s.user WHERE " +
           "LOWER(s.studentId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.phone) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.user.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY s.fullName ASC")
    List<StudentProfile> searchStudents(@Param("query") String query);
}
