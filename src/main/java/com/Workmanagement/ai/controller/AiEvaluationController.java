package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.service.AiEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai/evaluations")
public class AiEvaluationController {

    private final AiEvaluationService evaluationService;

    public AiEvaluationController(
            AiEvaluationService evaluationService) {

        this.evaluationService = evaluationService;
    }

    // Create manual evaluation
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiEvaluation> createEvaluation(
            @RequestParam Long submissionId,
            @RequestBody AiEvaluation evaluation) {

        return ResponseEntity.ok(
                evaluationService.createEvaluation(
                        submissionId,
                        evaluation
                )
        );
    }

    // AI evaluation
    @PostMapping("/evaluate/{submissionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiEvaluation> evaluateSubmission(
            @PathVariable Long submissionId) {

        return ResponseEntity.ok(
                evaluationService.evaluateSubmission(submissionId)
        );
    }

    // Get evaluation by ID
    @GetMapping("/{id}")
    public ResponseEntity<AiEvaluation> getEvaluationById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                evaluationService.getEvaluationById(id)
        );
    }

    // Get evaluation for submission
    @GetMapping("/submission/{submissionId}")
    public ResponseEntity<AiEvaluation> getBySubmission(
            @PathVariable Long submissionId) {

        return ResponseEntity.ok(
                evaluationService.getEvaluationsBySubmission(
                        submissionId
                )
        );
    }

    // Get evaluations by status
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<AiEvaluation>> getByStatus(
            @PathVariable AiEvaluationStatus status) {

        return ResponseEntity.ok(
                evaluationService.getEvaluationsByStatus(status)
        );
    }

    // Update evaluation
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiEvaluation> updateEvaluation(
            @PathVariable Long id,
            @RequestBody AiEvaluation evaluation) {

        return ResponseEntity.ok(
                evaluationService.updateEvaluation(
                        id,
                        evaluation
                )
        );
    }
}