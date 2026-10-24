package com.brightwood.students.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal SchoolUserPrincipal user) {
        return new UserProfile(user.id(), user.username(), user.role(), user.studentId());
    }

    public record UserProfile(long id, String username, String role, Long studentId) {
    }
}
