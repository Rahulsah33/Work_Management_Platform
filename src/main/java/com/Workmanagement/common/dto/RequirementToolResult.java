package com.Workmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI-tool friendly projection of a TaskRequirement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementToolResult {

    private Long requirementId;
    private String description;
    private Integer weight;
    private Boolean mandatory;
}
