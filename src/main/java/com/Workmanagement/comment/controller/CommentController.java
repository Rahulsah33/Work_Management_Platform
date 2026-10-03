package com.Workmanagement.comment.controller;

import com.Workmanagement.comment.model.CommentResponse;
import com.Workmanagement.comment.model.CreateCommentRequest;
import com.Workmanagement.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Comments", description = "Endpoints for task commenting, feedback discussions, and author comment management")
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "Add comment to a task", description = "Creates a comment on a task. Author is securely derived from the authenticated user. Employees can comment on assigned tasks; Managers on tasks in managed projects; Admins on any task.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comment created successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is not authorized to comment on this task")
    })
    @PostMapping("/task/{taskId}")
    public ResponseEntity<CommentResponse> createComment(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId,
            @RequestBody CreateCommentRequest request
    ) {
        CommentResponse response = commentService.createComment(taskId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get comments for a task", description = "Retrieves all comments associated with a task. Enforces task access authorization.")
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<CommentResponse>> getTaskComments(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId
    ) {
        return ResponseEntity.ok(commentService.getTaskComments(taskId));
    }

    @Operation(summary = "Get current user's comments", description = "Retrieves all comments authored by the currently logged-in user.")
    @GetMapping("/me")
    public ResponseEntity<List<CommentResponse>> getMyComments() {
        return ResponseEntity.ok(commentService.getMyComments());
    }

    @Operation(summary = "Update comment", description = "Updates content of a comment. Only the original author can edit their comment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if authenticated user is not the author")
    })
    @PutMapping("/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @Parameter(description = "Comment ID", required = true) @PathVariable Long commentId,
            @RequestBody CreateCommentRequest request
    ) {
        return ResponseEntity.ok(commentService.updateComment(commentId, request));
    }

    @Operation(summary = "Delete comment", description = "Deletes a comment. Allowed for the original author or any ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comment deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if authenticated user is not the author or an admin")
    })
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @Parameter(description = "Comment ID", required = true) @PathVariable Long commentId
    ) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}
