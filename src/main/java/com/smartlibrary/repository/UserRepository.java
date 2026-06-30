package com.smartlibrary.repository;

import com.smartlibrary.entity.User;
import com.smartlibrary.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    long countByRole(UserRole role);

    List<User> findByRoleOrderByUsernameAsc(UserRole role);
}
