package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.entity.WorkRisk;
import com.Workmanagement.ai.model.AiRiskResult;
import com.Workmanagement.ai.service.AiRiskService;
import com.Workmanagement.ai.service.WorkRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI Risk", description = "Endpoints for task risk analysis, deadline heuristics, and AI-driven risk mitigation recommendations")
@RestController
@RequestMapping("/api/ai/risk")
public class WorkRiskController {

    private final WorkRiskService workRiskService;
    private final AiRiskService aiRiskService;

    public WorkRiskController(WorkRiskService workRiskService, AiRiskService aiRiskService) {
        this.workRiskService = workRiskService;
        this.aiRiskService = aiRiskService;
    }

    @Operation(summary = "Analyze task risk (Heuristic)", description = "Analyzes risk level (LOW, MEDIUM, HIGH) for a task based on deadlines, requirement complexity, and current progress. Requires ADMIN or project MANAGER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Risk analysis result calculated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<WorkRisk> analyzeTaskRisk(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId
    ) {

        return ResponseEntity.ok(
                workRiskService.analyzeTaskRisk(taskId)
        );
    }

    @Operation(summary = "Analyze task risk with AI GenAI", description = "Uses AI prompt evaluation to generate in-depth risk analysis, failure probabilities, explanations, and actionable mitigation recommendations. Requires ADMIN or project MANAGER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI risk analysis result returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @GetMapping("/task/{taskId}/ai")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AiRiskResult> analyzeAiRisk(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(
                aiRiskService.analyzeRisk(taskId)
        );
    }
}