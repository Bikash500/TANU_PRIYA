package com.brightwood.students.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SchoolUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public SchoolUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return jdbcTemplate.query("""
                SELECT id, username, password_hash, role, student_id
                FROM users
                WHERE username = ? COLLATE NOCASE
                """, resultSet -> {
            if (!resultSet.next()) {
                throw new UsernameNotFoundException("User account was not found.");
            }
            long studentId = resultSet.getLong("student_id");
            Long linkedStudentId = resultSet.wasNull() ? null : studentId;
            return new SchoolUserPrincipal(
                    resultSet.getLong("id"),
                    resultSet.getString("username"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("role"),
                    linkedStudentId
            );
        }, username);
    }
}
