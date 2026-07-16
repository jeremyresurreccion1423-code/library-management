package com.smartlibrary.repository;

import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);

    List<User> findByRoleOrderByUsernameAsc(UserRole role);

    List<User> findByLockedUntilAfter(LocalDateTime time);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts, u.lockedUntil = :lockedUntil WHERE u.id = :id")
    void updateLockoutState(@Param("id") Long id,
                            @Param("attempts") Integer attempts,
                            @Param("lockedUntil") LocalDateTime lockedUntil);
}
