package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.service.AiEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI Evaluation", description = "Endpoints for automated and manual AI evaluations of submissions against task requirements")
@RestController
@RequestMapping("/api/ai/evaluations")
public class AiEvaluationController {

    private final AiEvaluationService evaluationService;

    public AiEvaluationController(
            AiEvaluationService evaluationService) {

        this.evaluationService = evaluationService;
    }

    @Operation(summary = "Create manual evaluation", description = "Creates an evaluation record manually for a submission. Requires ADMIN or project MANAGER ownership.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluation created successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiEvaluation> createEvaluation(
            @Parameter(description = "Submission ID", required = true) @RequestParam Long submissionId,
            @RequestBody AiEvaluation evaluation) {

        return ResponseEntity.ok(
                evaluationService.createEvaluation(
                        submissionId,
                        evaluation
                )
        );
    }

    @Operation(summary = "Trigger automated AI evaluation", description = "Evaluates a submission against its task requirements using Google GenAI / Gemini, computing completion percentage, quality score, and feedback.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI evaluation completed and stored"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @PostMapping("/evaluate/{submissionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiEvaluation> evaluateSubmission(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long submissionId) {

        return ResponseEntity.ok(
                evaluationService.evaluateSubmission(submissionId)
        );
    }

    @Operation(summary = "Get evaluation by ID", description = "Retrieves an AI evaluation by its ID. Enforces submission and task ownership access.")
    @GetMapping("/{id}")
    public ResponseEntity<AiEvaluation> getEvaluationById(
            @Parameter(description = "Evaluation ID", required = true) @PathVariable Long id) {

        return ResponseEntity.ok(
                evaluationService.getEvaluationById(id)
        );
    }

    @Operation(summary = "Get evaluation for submission", description = "Retrieves the AI evaluation associated with a submission.")
    @GetMapping("/submission/{submissionId}")
    public ResponseEntity<AiEvaluation> getBySubmission(
            @Parameter(description = "Submission ID", required = true) @PathVariable Long submissionId) {

        return ResponseEntity.ok(
                evaluationService.getEvaluationsBySubmission(
                        submissionId
                )
        );
    }

    @Operation(summary = "Get evaluations by status", description = "Retrieves all evaluations filtered by status (PENDING, COMPLETED, FAILED). Requires ADMIN or MANAGER role.")
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AiEvaluation>> getByStatus(
            @Parameter(description = "AI evaluation status", required = true) @PathVariable AiEvaluationStatus status) {

        return ResponseEntity.ok(
                evaluationService.getEvaluationsByStatus(status)
        );
    }

    @Operation(summary = "Update evaluation", description = "Updates an existing evaluation record. Requires project manager ownership or admin.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiEvaluation> updateEvaluation(
            @Parameter(description = "Evaluation ID", required = true) @PathVariable Long id,
            @RequestBody AiEvaluation evaluation) {

        return ResponseEntity.ok(
                evaluationService.updateEvaluation(
                        id,
                        evaluation
                )
        );
    }
}