package com.Workmanagement.integration;

import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.model.CreateCommentRequest;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class CommentApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/comments/task/{taskId} - Assigned employee adds comment; Returns 201 CREATED")
    void testCreateCommentSuccess() throws Exception {
        User manager = createUser("Manager", "mgr@comment.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@comment.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        CreateCommentRequest request = new CreateCommentRequest("Working on unit tests today.");

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content").value("Working on unit tests today."))
                .andExpect(jsonPath("$.authorName").value("Emp"))
                .andExpect(jsonPath("$.authorRole").value("EMPLOYEE"));

        assertEquals(1, commentRepository.count());
    }

    @Test
    @DisplayName("POST /api/comments/task/{taskId} - Unassigned employee cannot comment (403 FORBIDDEN)")
    void testCreateCommentUnassignedEmployeeForbidden() throws Exception {
        User manager = createUser("Manager", "mgr@comment.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@comment.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@comment.com", "pass", Role.EMPLOYEE);

        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task for Emp 1", "Desc", project, emp1, TaskStatus.IN_PROGRESS);

        CreateCommentRequest request = new CreateCommentRequest("Attempting unauthorized comment.");

        mockMvc.perform(post("/api/comments/task/" + task.getId())
                        .cookie(createAuthCookie(emp2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/comments/task/{taskId} - Retrieves task comments")
    void testGetTaskComments() throws Exception {
        User manager = createUser("Manager", "mgr@comment.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@comment.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        createComment(task, emp, "Comment 1");
        createComment(task, manager, "Comment 2");

        mockMvc.perform(get("/api/comments/task/" + task.getId()).cookie(createAuthCookie(emp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /api/comments/{commentId} - Only author can update comment; Non-author gets 403")
    void testUpdateCommentAuthorOnly() throws Exception {
        User manager = createUser("Manager", "mgr@comment.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@comment.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@comment.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp1, TaskStatus.IN_PROGRESS);
        Comment comment = createComment(task, emp1, "Original comment");

        CreateCommentRequest updateReq = new CreateCommentRequest("Updated comment by author");

        // Emp 2 tries to update Emp 1's comment -> 403
        mockMvc.perform(put("/api/comments/" + comment.getId())
                        .cookie(createAuthCookie(emp2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // Emp 1 updates own comment -> 200
        mockMvc.perform(put("/api/comments/" + comment.getId())
                        .cookie(createAuthCookie(emp1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated comment by author"));
    }

    @Test
    @DisplayName("DELETE /api/comments/{commentId} - Author and Admin can delete comment; Non-author employee gets 403")
    void testDeleteCommentAccess() throws Exception {
        User admin = createUser("Admin", "admin@comment.com", "pass", Role.ADMIN);
        User manager = createUser("Manager", "mgr@comment.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@comment.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@comment.com", "pass", Role.EMPLOYEE);

        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp1, TaskStatus.IN_PROGRESS);
        Comment comment = createComment(task, emp1, "Comment to delete");

        // Emp 2 tries to delete Emp 1's comment -> 403
        mockMvc.perform(delete("/api/comments/" + comment.getId()).cookie(createAuthCookie(emp2)))
                .andExpect(status().isForbidden());

        // Admin deletes comment -> 204 No Content
        mockMvc.perform(delete("/api/comments/" + comment.getId()).cookie(createAuthCookie(admin)))
                .andExpect(status().isNoContent());

        assertFalse(commentRepository.existsById(comment.getId()));
    }
}
