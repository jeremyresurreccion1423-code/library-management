package com.smartlibrary.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SharedAuthPasswordRehashRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SharedAuthPasswordRehashRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public SharedAuthPasswordRehashRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, password FROM public.users WHERE password IS NOT NULL");
        int updated = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String password = (String) row.get("password");
            if (password == null || password.startsWith("$2a$") || password.startsWith("$2b$")) {
                continue;
            }
            jdbcTemplate.update("UPDATE public.users SET password = ? WHERE id = ?",
                    passwordEncoder.encode(password), id);
            updated++;
        }
        if (updated > 0) {
            log.info("Re-hashed {} plain-text password(s) to BCrypt in public.users", updated);
        }
    }
}
