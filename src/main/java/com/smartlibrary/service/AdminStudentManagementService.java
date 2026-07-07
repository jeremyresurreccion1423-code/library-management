package com.smartlibrary.service;

import com.smartlibrary.entity.StudentProfile;
import com.smartlibrary.entity.User;
import com.smartlibrary.model.IssueStatus;
import com.smartlibrary.model.UserRole;
import com.smartlibrary.repository.BookIssueRepository;
import com.smartlibrary.repository.ReservationRepository;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AdminStudentManagementService {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final BookIssueRepository bookIssueRepository;
    private final ReservationRepository reservationRepository;

    public AdminStudentManagementService(
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            BookIssueRepository bookIssueRepository,
            ReservationRepository reservationRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.bookIssueRepository = bookIssueRepository;
        this.reservationRepository = reservationRepository;
    }

    public List<StudentProfile> listStudents(String query, boolean showArchived) {
        List<StudentProfile> students;
        if (query != null && !query.isBlank()) {
            students = studentProfileRepository.searchStudents(query.trim());
        } else {
            students = studentProfileRepository.findAllWithUsers();
        }
        return students.stream()
                .filter(student -> showArchived == student.isArchived())
                .toList();
    }

    public Optional<StudentProfile> findByStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return Optional.empty();
        }
        return studentProfileRepository.findByStudentIdWithUser(studentId.trim());
    }

    @Transactional
    public void archive(Long profileId) {
        StudentProfile profile = requireProfile(profileId);
        profile.setArchived(true);
        studentProfileRepository.save(profile);
        disableUser(profile.getUser());
    }

    @Transactional
    public void restore(Long profileId) {
        StudentProfile profile = requireProfile(profileId);
        profile.setArchived(false);
        studentProfileRepository.save(profile);
        if (profile.getUser() != null && profile.getUser().getRole() == UserRole.STUDENT) {
            User user = profile.getUser();
            user.setEnabled(true);
            userRepository.save(user);
        }
    }

    @Transactional
    public void delete(Long profileId) {
        StudentProfile profile = requireProfile(profileId);
        Long id = Objects.requireNonNull(profile.getId());

        if (bookIssueRepository.existsByStudent_IdAndStatusIn(id, List.of(IssueStatus.BORROWED, IssueStatus.OVERDUE))) {
            throw new IllegalStateException("Cannot delete: student still has active borrowed books. Archive instead.");
        }
        if (bookIssueRepository.countByStudent_Id(id) > 0) {
            throw new IllegalStateException("Cannot delete: student has borrowing history. Archive instead to keep records.");
        }
        if (reservationRepository.countByStudent_Id(id) > 0) {
            throw new IllegalStateException("Cannot delete: student has reservation records. Archive instead.");
        }

        User user = profile.getUser();
        studentProfileRepository.delete(profile);
        if (user != null) {
            user.setStudentProfile(null);
            user.setEnabled(false);
            userRepository.save(user);
        }
    }

    private StudentProfile requireProfile(Long profileId) {
        return studentProfileRepository.findById(Objects.requireNonNull(profileId))
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
    }

    private void disableUser(User user) {
        if (user != null) {
            user.setEnabled(false);
            userRepository.save(user);
        }
    }
}
