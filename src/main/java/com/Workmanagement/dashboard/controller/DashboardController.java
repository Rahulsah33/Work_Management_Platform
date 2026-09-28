package com.Workmanagement.dashboard.controller;

import com.Workmanagement.dashboard.model.EmployeeDashboardResponse;
import com.Workmanagement.dashboard.model.ManagerDashboardResponse;
import com.Workmanagement.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard APIs consumed by the frontend.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // Manager/Admin statistics
    @GetMapping("/manager")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ManagerDashboardResponse> managerDashboard() {
        return ResponseEntity.ok(dashboardService.getManagerDashboard());
    }

    // Employee dashboard - uses the authenticated JWT user,
    // NOT an id supplied by the client.
    @GetMapping("/employee")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<EmployeeDashboardResponse> employeeDashboard(Authentication authentication) {
        return ResponseEntity.ok(
                dashboardService.getEmployeeDashboard(authentication.getName()));
    }
}
