package com.Workmanagement.ai.service;

import com.Workmanagement.ai.model.RequirementEvaluation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequirementScoreCalculator {

    public double calculateCompletionPercentage(
            List<RequirementEvaluation> requirements) {

        if (requirements == null || requirements.isEmpty()) {
            return 0.0;
        }

        double totalWeight = 0.0;
        double achievedWeight = 0.0;

        for (RequirementEvaluation requirement : requirements) {

            if (requirement.getWeight() == null ||
                    requirement.getStatus() == null) {
                continue;
            }

            double weight = requirement.getWeight();

            totalWeight += weight;

            String status = requirement.getStatus()
                    .trim()
                    .toUpperCase();

            switch (status) {

                case "COMPLETED":
                    achievedWeight += weight;
                    break;

                case "PARTIAL":
                    achievedWeight += weight * 0.5;
                    break;

                case "MISSING":
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Invalid requirement status: " + status
                    );
            }
        }

        if (totalWeight == 0) {
            return 0.0;
        }

        double percentage = (achievedWeight / totalWeight) * 100;

        return Math.round(percentage * 100.0) / 100.0;
    }
}