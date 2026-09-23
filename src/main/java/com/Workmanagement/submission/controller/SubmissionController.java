package com.Workmanagement.submission.controller;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.service.SubmissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    // Submit work for a task using path variable
    @PostMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Submission> submitTaskWork(
            @PathVariable Long taskId,
            @RequestBody Submission submission,
            Authentication authentication) {

        Submission saved = submissionService.createSubmission(taskId, submission, authentication.getName());
        return ResponseEntity.ok(saved);
    }

    // Submit work for a task using query param
    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Submission> submitWorkWithQueryParam(
            @RequestParam Long taskId,
            @RequestBody Submission submission,
            Authentication authentication) {

        Submission saved = submissionService.createSubmission(taskId, submission, authentication.getName());
        return ResponseEntity.ok(saved);
    }

    // Get submission by ID
    @GetMapping("/{id}")
    public ResponseEntity<Submission> getSubmissionById(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.getSubmissionById(id));
    }

    // Get all submissions for a task
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<Submission>> getSubmissionsByTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(submissionService.getSubmissionsByTaskId(taskId));
    }

    // Get my submissions (authenticated user)
    @GetMapping("/my")
    public ResponseEntity<List<Submission>> getMySubmissions(Authentication authentication) {
        return ResponseEntity.ok(submissionService.getMySubmissions(authentication.getName()));
    }

    // Get submissions by employee ID
    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Submission>> getSubmissionsByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(submissionService.getSubmissionsByEmployee(employeeId));
    }

    // Get all submissions (optionally filter by status)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Submission>> getAllSubmissions(
            @RequestParam(required = false) SubmissionStatus status) {

        if (status != null) {
            return ResponseEntity.ok(submissionService.getSubmissionsByStatus(status));
        }
        return ResponseEntity.ok(submissionService.getAllSubmissions());
    }

    // Review submission (APPROVE, CHANGES_REQUESTED, etc.)
    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Submission> reviewSubmission(
            @PathVariable Long id,
            @RequestParam SubmissionStatus status) {

        Submission reviewed = submissionService.reviewSubmission(id, status);
        return ResponseEntity.ok(reviewed);
    }

    // Delete submission
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteSubmission(@PathVariable Long id) {
        submissionService.deleteSubmission(id);
        return ResponseEntity.noContent().build();
    }
}
