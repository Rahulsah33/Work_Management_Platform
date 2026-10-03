package com.Workmanagement.dashboard.controller;

import com.Workmanagement.dashboard.model.EmployeeDashboardResponse;
import com.Workmanagement.dashboard.service.EmployeeDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employee Dashboard", description = "Endpoints for employee task metrics, personal submission history, and AI evaluation averages")
@RestController
@RequestMapping("/api/dashboard")
public class EmployeeDashboardController {

    private final EmployeeDashboardService employeeDashboardService;

    public EmployeeDashboardController(EmployeeDashboardService employeeDashboardService) {
        this.employeeDashboardService = employeeDashboardService;
    }

    @Operation(summary = "Get employee dashboard metrics", description = "Retrieves personal task statistics, task summaries, submission statistics, AI evaluation averages, and recent submissions for the authenticated employee (derived from session, no employeeId param).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee dashboard data returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is not EMPLOYEE")
    })
    @GetMapping("/employee")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
    public ResponseEntity<EmployeeDashboardResponse> getEmployeeDashboard() {
        return ResponseEntity.ok(employeeDashboardService.getDashboard());
    }
}
