package com.brightwood.students.audit;

import java.util.List;

import com.brightwood.students.security.SchoolUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditService.AuditEntry> list(
            @RequestParam(defaultValue = "100") int limit
    ) {
        return auditService.findRecent(limit);
    }

    @GetMapping("/student-activity")
    @PreAuthorize("hasRole('GUARDIAN')")
    public List<AuditService.AuditEntry> studentActivity(
            @AuthenticationPrincipal SchoolUserPrincipal user
    ) {
        if (user.studentId() == null) {
            throw new AccessDeniedException("This guardian account is not linked to a student.");
        }
        return auditService.findStudentActivity(user.studentId());
    }
}
