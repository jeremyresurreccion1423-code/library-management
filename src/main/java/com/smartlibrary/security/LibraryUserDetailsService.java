package com.smartlibrary.security;

import com.smartlibrary.entity.User;
import com.smartlibrary.repository.StudentProfileRepository;
import com.smartlibrary.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LibraryUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    public LibraryUserDetailsService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrIdOrEmail) throws UsernameNotFoundException {
        String key = usernameOrIdOrEmail == null ? "" : usernameOrIdOrEmail.trim();
        if (key.isEmpty()) {
            throw new UsernameNotFoundException("empty");
        }

        Optional<User> user = userRepository.findByUsername(key);

        if (user.isEmpty()) {
            user = studentProfileRepository.findByStudentIdWithUser(key).map(profile -> profile.getUser());
        }

        if (user.isEmpty()) {
            user = userRepository.findByEmailIgnoreCase(key);
        }

        return user.map(LibraryUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(key));
    }
}
