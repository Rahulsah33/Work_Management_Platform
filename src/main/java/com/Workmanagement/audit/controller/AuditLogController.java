package com.Workmanagement.audit.controller;

import com.Workmanagement.audit.model.AuditLogResponse;
import com.Workmanagement.audit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Audit Logs", description = "Append-only audit trail capturing administrative, manager, employee, and AI system events")
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "Get all audit logs (ADMIN only)", description = "Retrieves the complete global audit trail across all entities and users. Accessible strictly to users with the ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Global audit log list"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is not an ADMIN")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> getAllLogs() {
        return ResponseEntity.ok(auditLogService.getAllLogs());
    }

    @Operation(summary = "Get audit logs for authenticated user", description = "Retrieves audit records representing actions performed by the currently logged-in user.")
    @GetMapping("/me")
    public ResponseEntity<List<AuditLogResponse>> getMyLogs() {
        return ResponseEntity.ok(auditLogService.getMyLogs());
    }

    @Operation(summary = "Get audit logs for a specific entity", description = "Retrieves audit history for a target entity (e.g., PROJECT, TASK, SUBMISSION) by its type and ID. Scoped by caller access domain.")
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLogResponse>> getEntityLogs(
            @Parameter(description = "Entity type (e.g. PROJECT, TASK, SUBMISSION, TASK_REQUIREMENT, NOTIFICATION, COMMENT)", required = true) @PathVariable String entityType,
            @Parameter(description = "Entity database ID", required = true) @PathVariable Long entityId
    ) {
        return ResponseEntity.ok(auditLogService.getEntityLogs(entityType, entityId));
    }
}
