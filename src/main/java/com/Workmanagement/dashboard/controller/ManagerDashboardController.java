package com.Workmanagement.dashboard.controller;

import com.Workmanagement.dashboard.model.ManagerDashboardResponse;
import com.Workmanagement.dashboard.service.ManagerDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Manager Dashboard", description = "Endpoints for aggregated manager project, task, submission, workload, and AI quality metrics")
@RestController
@RequestMapping("/api/dashboard")
public class ManagerDashboardController {

    private final ManagerDashboardService managerDashboardService;

    public ManagerDashboardController(ManagerDashboardService managerDashboardService) {
        this.managerDashboardService = managerDashboardService;
    }

    @Operation(summary = "Get manager dashboard metrics", description = "Retrieves aggregated metrics (projects, tasks, submission counts, AI score averages, employee workloads, and project summaries) scoped strictly to projects managed by the authenticated manager.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manager dashboard data returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is not ADMIN or MANAGER")
    })
    @GetMapping("/manager")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ManagerDashboardResponse> getManagerDashboard() {
        return ResponseEntity.ok(managerDashboardService.getDashboard());
    }
}
