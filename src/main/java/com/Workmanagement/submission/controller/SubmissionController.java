package com.Workmanagement.submission.controller;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Submissions", description = "Endpoints for employee work submissions, review workflows, approvals, and resubmissions")
@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(
            SubmissionService submissionService) {

        this.submissionService = submissionService;
    }

    @Operation(summary = "Submit work for a task", description = "Employee submits work (report, links, evidence) for an assigned task. The submitter is securely derived from the authenticated JWT session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submission successfully created"),
            @ApiResponse(responseCode = "403", description = "Forbidden if task is not assigned to the authenticated employee")
    })
    @PostMapping
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Submission> createSubmission(
            @Parameter(description = "ID of the task being submitted", required = false) @RequestParam(required = false) Long taskId,
            @Parameter(description = "Optional legacy employee ID (ignored; submitter is always bound to authenticated employee)", required = false) @RequestParam(required = false) Long employeeId,
            @RequestBody(required = false) Submission submission) {

        Long effectiveTaskId = taskId != null ? taskId : (submission != null && submission.getTask() != null ? submission.getTask().getId() : null);

        return ResponseEntity.ok(
                submissionService.createSubmission(
                        effectiveTaskId,
                        employeeId,
                        submission)
        );
    }

    @Operation(summary = "Get all submissions", description = "Retrieves all submissions accessible to authenticated user (all for ADMIN, managed projects for MANAGER, own for EMPLOYEE).")
    @GetMapping
    public ResponseEntity<List<Submission>> getAllSubmissions() {
        return ResponseEntity.ok(
                submissionService.getAllSubmissions()
        );
    }

    @Operation(summary = "Get current employee's submissions", description = "Retrieves all submissions submitted by the authenticated employee.")
    @GetMapping("/me")
    public ResponseEntity<List<Submission>> getMySubmissions() {
        return ResponseEntity.ok(
                submissionService.getMySubmissions()
        );
    }

    @Operation(summary = "Get submission by ID", description = "Retrieves submission details. Scoped by task assignment for employees and project ownership for managers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submission found"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is not authorized to access this submission")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Submission> getSubmission(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long id) {

        return ResponseEntity.ok(
                submissionService.getSubmissionById(id)
        );
    }

    @Operation(summary = "Get submissions for a task", description = "Retrieves submissions for a specific task. Accessible by assigned employee, managing manager, or admin.")
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<Submission>> getByTask(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId) {

        return ResponseEntity.ok(
                submissionService.getSubmissionsByTask(taskId)
        );
    }

    @Operation(summary = "Get submissions by employee", description = "Retrieves submissions by employee ID. Employees can only view own; Managers can view submissions for tasks in their projects; Admins have global access.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of employee submissions"),
            @ApiResponse(responseCode = "403", description = "Forbidden on cross-employee inspection")
    })
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Submission>> getByEmployee(
            @Parameter(description = "Employee ID", required = true) @PathVariable Long employeeId) {

        return ResponseEntity.ok(
                submissionService.getSubmissionsByEmployee(employeeId)
        );
    }

    @Operation(summary = "Get submissions by status", description = "Retrieves submissions matching a specific status (SUBMITTED, UNDER_REVIEW, APPROVED, CHANGES_REQUESTED). Requires ADMIN or MANAGER role.")
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Submission>> getByStatus(
            @Parameter(description = "Submission status", required = true) @PathVariable SubmissionStatus status) {

        return ResponseEntity.ok(
                submissionService.getSubmissionsByStatus(status)
        );
    }

    @Operation(summary = "Update submission status", description = "Updates status of a submission. Requires project manager ownership or admin.")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Submission> updateStatus(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long id,
            @Parameter(description = "New status", required = true) @RequestParam SubmissionStatus status) {

        return ResponseEntity.ok(
                submissionService.updateStatus(
                        id,
                        status)
        );
    }

    @Operation(summary = "Approve submission", description = "Approves a submission, marking it APPROVED and completing the parent task. Requires project manager ownership or admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submission approved"),
            @ApiResponse(responseCode = "400", description = "Bad Request if submission not under review or already approved"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @RequestMapping(value = "/{id}/approve", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Submission> approveSubmission(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long id,
            @RequestBody(required = false) com.Workmanagement.submission.dto.ReviewSubmissionRequest request
    ) {
        String feedback = request != null ? request.getFeedback() : null;
        return ResponseEntity.ok(
                submissionService.approveSubmission(id, feedback)
        );
    }

    @Operation(summary = "Request changes on submission", description = "Requests revisions on a submission, transitioning status to CHANGES_REQUESTED. Requires project manager ownership or admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Changes requested successfully"),
            @ApiResponse(responseCode = "400", description = "Bad Request if feedback missing or submission not under review"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @RequestMapping(value = "/{id}/request-changes", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Submission> requestChanges(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long id,
            @RequestBody(required = false) com.Workmanagement.submission.dto.ReviewSubmissionRequest request
    ) {
        String feedback = request != null ? request.getFeedback() : null;
        return ResponseEntity.ok(
                submissionService.requestChanges(id, feedback)
        );
    }

    @Operation(summary = "Resubmit work after changes requested", description = "Employee resubmits modified work for a submission that had changes requested.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Work resubmitted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad Request if submission not in CHANGES_REQUESTED state or report is empty"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is not the assigned submitter")
    })
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Submission> resubmit(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long id,
            @RequestBody Submission submission
    ) {
        return ResponseEntity.ok(
                submissionService.resubmit(id, submission)
        );
    }

    @Operation(summary = "Get submission version history for a task", description = "Retrieves complete iteration history with evaluations and feedback for a task.")
    @GetMapping("/task/{taskId}/history")
    public ResponseEntity<List<com.Workmanagement.submission.dto.SubmissionHistoryDto>> getTaskSubmissionHistory(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId) {

        return ResponseEntity.ok(
                submissionService.getSubmissionHistory(taskId)
        );
    }

    @Operation(summary = "Get submission version history for a submission", description = "Retrieves complete iteration history for the task associated with the given submission ID.")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<com.Workmanagement.submission.dto.SubmissionHistoryDto>> getSubmissionHistory(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long id) {

        return ResponseEntity.ok(
                submissionService.getSubmissionHistoryForSubmission(id)
        );
    }

    @Operation(summary = "Get latest submission for a task", description = "Retrieves the current/latest iteration submission for a task.")
    @GetMapping("/task/{taskId}/latest")
    public ResponseEntity<Submission> getLatestSubmissionForTask(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId) {

        Submission latest = submissionService.getLatestSubmissionForTask(taskId);
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }
}