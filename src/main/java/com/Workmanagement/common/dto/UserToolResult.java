package com.Workmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI-tool friendly projection of a User/Employee.
 * <p>
 * SECURITY: this DTO intentionally has NO password field and
 * never exposes any authentication data. It is safe to return
 * from AI tools and REST APIs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserToolResult {

    private Long userId;
    private String name;
    private String email;
    private String role;
}
