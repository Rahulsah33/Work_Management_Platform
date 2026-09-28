package com.Workmanagement.comment.service;

import com.Workmanagement.comment.dto.CommentRequest;
import com.Workmanagement.comment.dto.CommentResponse;
import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository,
                          TaskRepository taskRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // CREATE COMMENT ON A TASK
    // =========================================================

    @Transactional
    public CommentResponse addComment(Long taskId, CommentRequest request, String email) {

        User user = resolveUser(email);
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        // Ownership / role rules:
        // - EMPLOYEE may only comment on tasks assigned to them.
        // - MANAGER / ADMIN may comment on any task.
        if (user.getRole() == Role.EMPLOYEE) {
            if (task.getAssignedTo() == null
                    || !task.getAssignedTo().getId().equals(user.getId())) {
                throw new RuntimeException(
                        "You can only comment on tasks assigned to you");
            }
        }

        Comment comment = Comment.builder()
                .task(task)
                .user(user)
                .message(request.getMessage())
                .build();

        return toResponse(commentRepository.save(comment));
    }

    // =========================================================
    // LIST COMMENTS FOR A TASK
    // =========================================================

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForTask(Long taskId, String email) {

        User user = resolveUser(email);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        // Employee may only read discussion of their own task.
        if (user.getRole() == Role.EMPLOYEE) {
            if (task.getAssignedTo() == null
                    || !task.getAssignedTo().getId().equals(user.getId())) {
                throw new RuntimeException(
                        "You can only view comments on tasks assigned to you");
            }
        }

        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // DELETE COMMENT (author or ADMIN)
    // =========================================================

    @Transactional
    public void deleteComment(Long commentId, String email) {

        User user = resolveUser(email);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        boolean isAuthor = comment.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new RuntimeException(
                    "You can only delete your own comments");
        }

        commentRepository.delete(comment);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with email: " + email));
    }

    private CommentResponse toResponse(Comment c) {
        User author = c.getUser();
        return CommentResponse.builder()
                .id(c.getId())
                .taskId(c.getTask() != null ? c.getTask().getId() : null)
                .userId(author != null ? author.getId() : null)
                .userName(author != null ? author.getName() : null)
                .userRole(author != null && author.getRole() != null
                        ? author.getRole().name() : null)
                .message(c.getMessage())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
