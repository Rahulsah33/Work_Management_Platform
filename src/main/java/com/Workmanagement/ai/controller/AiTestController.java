package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.ai.service.AiPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI Test", description = "Endpoints for direct testing of AI prompt generation and requirement scoring")
@RestController
@RequestMapping("/api/ai")
public class AiTestController {

    private final AiPromptService aiPromptService;

    public AiTestController(AiPromptService aiPromptService) {
        this.aiPromptService = aiPromptService;
    }

    @Operation(summary = "Test AI evaluation prompt scoring", description = "Test utility endpoint for evaluating sample employee reports against requirements.")
    @PostMapping("/test")
    public AiEvaluationResult testEvaluation(
            @Parameter(description = "Task description", required = true) @RequestParam String taskDescription,
            @Parameter(description = "Requirements text", required = true) @RequestParam String requirements,
            @Parameter(description = "Employee report text", required = true) @RequestParam String employeeReport) {

        return aiPromptService.evaluationResult(
                taskDescription,
                requirements,
                employeeReport
        );
    }
}