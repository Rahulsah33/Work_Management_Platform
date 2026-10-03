package com.Workmanagement.comment.model;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long taskId,
        Long authorId,
        String authorName,
        String authorRole,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
