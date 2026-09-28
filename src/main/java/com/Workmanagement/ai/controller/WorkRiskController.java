package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.entity.WorkRisk;
import com.Workmanagement.ai.model.AiRiskResult;
import com.Workmanagement.ai.service.AiRiskService;
import com.Workmanagement.ai.service.WorkRiskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/risk")
public class WorkRiskController {

    private final WorkRiskService workRiskService;
    private final AiRiskService aiRiskService;

    public WorkRiskController(WorkRiskService workRiskService, AiRiskService aiRiskService) {
        this.workRiskService = workRiskService;
        this.aiRiskService = aiRiskService;
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<WorkRisk> analyzeTaskRisk(
            @PathVariable Long taskId
    ) {

        return ResponseEntity.ok(
                workRiskService.analyzeTaskRisk(taskId)
        );
    }

    @GetMapping("/task/{taskId}/ai")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiRiskResult> analyzeAiRisk(
            @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(
                aiRiskService.analyzeRisk(taskId)
        );
    }
}