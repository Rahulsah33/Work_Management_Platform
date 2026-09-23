package com.Workmanagement.submission.controller;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.service.SubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(
            SubmissionService submissionService) {

        this.submissionService = submissionService;
    }

    // Employee submits work for a task
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Submission> createSubmission(
            @RequestParam Long taskId,
            @RequestParam Long employeeId,
            @RequestBody Submission submission) {

        return ResponseEntity.ok(
                submissionService.createSubmission(
                        taskId,
                        employeeId,
                        submission)
        );
    }

    // Get submission by ID
    @GetMapping("/{id}")
    public ResponseEntity<Submission> getSubmission(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                submissionService.getSubmissionById(id)
        );
    }

    // Get all submissions for a task
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<Submission>> getByTask(
            @PathVariable Long taskId) {

        return ResponseEntity.ok(
                submissionService.getSubmissionsByTask(taskId)
        );
    }

    // Get all submissions by employee
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Submission>> getByEmployee(
            @PathVariable Long employeeId) {

        return ResponseEntity.ok(
                submissionService.getSubmissionsByEmployee(employeeId)
        );
    }

    // Get submissions by status
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Submission>> getByStatus(
            @PathVariable SubmissionStatus status) {

        return ResponseEntity.ok(
                submissionService.getSubmissionsByStatus(status)
        );
    }

    // Update submission status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Submission> updateStatus(
            @PathVariable Long id,
            @RequestParam SubmissionStatus status) {

        return ResponseEntity.ok(
                submissionService.updateStatus(
                        id,
                        status)
        );
    }
}