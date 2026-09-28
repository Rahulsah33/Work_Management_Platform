package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.ai.service.AiPromptService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiTestController {

    private final AiPromptService aiPromptService;

    public AiTestController(AiPromptService aiPromptService) {
        this.aiPromptService = aiPromptService;
    }

    @PostMapping("/test")
    public AiEvaluationResult testEvaluation(
            @RequestParam String taskDescription,
            @RequestParam String requirements,
            @RequestParam String employeeReport) {

        return aiPromptService.evaluationResult(
                taskDescription,
                requirements,
                employeeReport
        );
    }
}