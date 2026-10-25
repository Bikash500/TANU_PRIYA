package com.brightwood.students.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("production")
public class ProductionAdminInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProductionAdminInitializer.class);
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public ProductionAdminInitializer(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${school.bootstrap-admin.email:}") String email,
            @Value("${school.bootstrap-admin.password:}") String password
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class);
        if (adminCount != null && adminCount > 0) {
            return;
        }
        if (email.isBlank() || password.length() < 14 || password.length() > 100) {
            throw new IllegalStateException(
                    "Set INITIAL_ADMIN_EMAIL and a 14–100 character INITIAL_ADMIN_PASSWORD "
                            + "before the first production startup.");
        }
        jdbcTemplate.update("""
                INSERT INTO users (username, password_hash, role)
                VALUES (?, ?, 'ADMIN')
                """, email, passwordEncoder.encode(password));
        logger.info("Created the initial production administrator account.");
    }
}
