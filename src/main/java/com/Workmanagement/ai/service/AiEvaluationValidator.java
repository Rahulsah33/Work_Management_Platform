package com.Workmanagement.ai.service;

import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.ai.model.RequirementEvaluation;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AiEvaluationValidator {

    // Validates the complete AI evaluation result
    public void validate(AiEvaluationResult result) {

        if (result == null) {
            throw new IllegalArgumentException(
                    "AI evaluation result cannot be null."
            );
        }

        if (result.getRequirements() == null ||
                result.getRequirements().isEmpty()) {

            throw new IllegalArgumentException(
                    "AI did not return any requirement evaluations."
            );
        }

        validateRequirements(result.getRequirements());

        validateScores(
                result.getQualityScore(),
                "Quality score"
        );

        validateScores(
                result.getConfidenceScore(),
                "Confidence score"
        );
    }


    // Validates the list of requirement evaluations
    private void validateRequirements(
            List<RequirementEvaluation> requirements) {

        Set<Long> requirementIds = new HashSet<>();

        int totalWeight = 0;

        for (RequirementEvaluation requirement : requirements) {

            // Validate requirement ID
            if (requirement.getRequirementId() == null) {

                throw new IllegalArgumentException(
                        "Requirement ID cannot be null."
                );
            }


            // Prevent duplicate requirement IDs
            if (!requirementIds.add(
                    requirement.getRequirementId())) {

                throw new IllegalArgumentException(
                        "Duplicate requirement ID found: "
                                + requirement.getRequirementId()
                );
            }


            // Validate weight
            if (requirement.getWeight() == null ||
                    requirement.getWeight() <= 0) {

                throw new IllegalArgumentException(
                        "Requirement weight must be greater than 0."
                );
            }


            // Validate requirement description
            if (requirement.getRequirement() == null ||
                    requirement.getRequirement().isBlank()) {

                throw new IllegalArgumentException(
                        "Requirement description cannot be empty."
                );
            }


            // Validate status
            String status = requirement.getStatus();

            if (status == null ||
                    !(status.equalsIgnoreCase("COMPLETED")
                            || status.equalsIgnoreCase("PARTIAL")
                            || status.equalsIgnoreCase("MISSING"))) {

                throw new IllegalArgumentException(
                        "Invalid requirement status: " + status
                );
            }


            // Add weight
            totalWeight += requirement.getWeight();
        }


        // Requirement weights must equal 100%
        if (totalWeight != 100) {

            throw new IllegalArgumentException(
                    "Total requirement weight must be 100. "
                            + "Current total: " + totalWeight
            );
        }
    }


    // Validates scores between 0 and 100
    private void validateScores(
            Double score,
            String fieldName) {

        if (score == null) {

            throw new IllegalArgumentException(
                    fieldName + " cannot be null."
            );
        }

        if (score < 0 || score > 100) {

            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 100."
            );
        }
    }
}