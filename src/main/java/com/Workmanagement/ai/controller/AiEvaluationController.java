package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.service.AiEvaluationService;
import jakarta.persistence.PrePersist;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/ai/evaluations")
public class AiEvaluationController {

    private final AiEvaluationService evaluationService;

    public AiEvaluationController(AiEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    // Create Evaluation

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN' , 'MANAGER')")
    public ResponseEntity<AiEvaluation> createEvaluation(
            @RequestParam Long submissionId,
            @RequestBody AiEvaluation evaluation
    ) {
        return ResponseEntity.ok(evaluationService.createEvaluation(submissionId, evaluation));
    }

    // Get Evaluation By id
    @GetMapping("/{id}")
    public ResponseEntity<AiEvaluation> getEvaluationById(@PathVariable Long id) {
        return ResponseEntity.ok(evaluationService.getEvaluationById(id));
    }

    // Get evaluation for submission
    @GetMapping("/submission/{submissionId}")
    public ResponseEntity<AiEvaluation> getBySubmission(@PathVariable Long submissionId) {
        return ResponseEntity.ok(evaluationService.getEvaluationBySubmission(submissionId));
    }

    // Get Evaluation by status
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN' , 'MANAGER')")
    public ResponseEntity<List<AiEvaluation>> getByStatus(@PathVariable AiEvaluationStatus status) {
        return ResponseEntity.ok(evaluationService.getEvaluationsByStatus(status));
    }

    // update evaluation
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN' , 'MANAGER')")
    public ResponseEntity<AiEvaluation> updateEvaluation(
            @PathVariable Long id,
            @RequestBody AiEvaluation evaluation
    ) {
        return ResponseEntity.ok(evaluationService.updateEvaluation(id, evaluation));
    }
}
