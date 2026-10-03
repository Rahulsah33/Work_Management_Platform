package com.Workmanagement.ai.service;

import com.Workmanagement.ai.model.AiEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AiPromptService {

    private static final Logger log = LoggerFactory.getLogger(AiPromptService.class);

    private final ChatClient chatClient;
    private final RequirementScoreCalculator scoreCalculator;
    private final AiEvaluationValidator validator;

    public AiPromptService(
            ChatClient.Builder chatClientBuilder,
            RequirementScoreCalculator scoreCalculator,
            AiEvaluationValidator validator) {

        this.chatClient = chatClientBuilder.build();
        this.scoreCalculator = scoreCalculator;
        this.validator = validator;
    }

    public AiEvaluationResult evaluationResult(
            String taskDescription,
            String requirements,
            String employeeReport) {

        String prompt = """
        You are an AI work evaluation assistant.

        Evaluate the employee's submitted work against
        each task requirement.

        TASK:
        %s

        REQUIREMENTS:
        %s

        EMPLOYEE REPORT:
        %s

        Evaluate EVERY requirement.

        For every requirement, return:

        - requirementId: the exact requirement ID provided above
        - requirement: the exact requirement description
        - weight: the exact requirement weight
        - status: ONLY one of COMPLETED, PARTIAL, MISSING
        - explanation: clear reason for the status

        Scoring rules:

        COMPLETED = 100%% of requirement weight
        PARTIAL = 50%% of requirement weight
        MISSING = 0%% of requirement weight

        Also return:

        - qualityScore: number from 0 to 100
        - confidenceScore: number from 0 to 100
        - feedback: concise overall feedback

        Important rules:

        1. Evaluate every requirement.
        2. Do not invent requirements.
        3. Do not change requirement IDs.
        4. Do not change requirement weights.
        5. Do not use any status other than COMPLETED, PARTIAL, or MISSING.
        6. Return numbers for all scores.
        7. Return ONLY JSON.
        8. Do NOT use Markdown code fences.
        9. Do NOT include explanations outside the JSON.

        Return JSON matching exactly this structure:

        {
          "completionPercentage": 0,
          "qualityScore": 0,
          "confidenceScore": 0,
          "feedback": "",
          "requirements": [
            {
              "requirementId": 0,
              "requirement": "",
              "weight": 0,
              "status": "COMPLETED",
              "explanation": ""
            }
          ]
        }
        """.formatted(
                taskDescription,
                requirements,
                employeeReport
        );

        try {
            log.info("Starting AI evaluation for task submission");

            AiEvaluationResult result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .entity(AiEvaluationResult.class);

            validator.validate(result);

            double calculatedPercentage =
                    scoreCalculator.calculateCompletionPercentage(
                            result.getRequirements()
                    );

            result.setCompletionPercentage(calculatedPercentage);

            log.info("AI evaluation completed successfully: completion={}%", calculatedPercentage);
            return result;

        } catch (Exception e) {
            log.error("AI evaluation failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}