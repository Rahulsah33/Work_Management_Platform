package com.Workmanagement.ai.controller;

import com.Workmanagement.ai.service.AiAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI Agent", description = "Read-only AI assistant equipped with database querying tools for projects, tasks, and employees")
@RestController
@RequestMapping("/api/ai/agent")
public class AiAgentController {

    private final AiAgentService aiAgentService;

    public AiAgentController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    @Operation(
            summary = "Chat with AI work management assistant",
            description = "Submits a prompt to the read-only AI agent (e.g. 'Show me overdue tasks in my projects'). " +
                    "The agent queries database tools scoped to the caller's authorization domain (Manager or Admin). " +
                    "Request body expects a plain text message string."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI assistant response string"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is not ADMIN or MANAGER")
    })
    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<String> chat(
            @RequestBody String message
    ) {

        return ResponseEntity.ok(
                aiAgentService.chat(message)
        );
    }
}