package com.Workmanagement.integration;

import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.model.CreateCommentRequest;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SecurityPenetrationIntegrationTest extends BaseIntegrationTest {

    private User admin;
    private User managerA;
    private User managerB;
    private User empA;
    private User empB;

    private Project projectA;
    private Project projectB;

    private Task taskA;
    private Task taskB;

    private Submission submissionA;
    private Submission submissionB;

    private Notification notifB;
    private Comment commentB;

    @BeforeEach
    void setUpMatrix() {
        admin = createUser("Admin", "admin@pen.com", "pass", Role.ADMIN);
        managerA = createUser("Manager A", "mgra@pen.com", "pass", Role.MANAGER);
        managerB = createUser("Manager B", "mgrb@pen.com", "pass", Role.MANAGER);
        empA = createUser("Employee A", "empa@pen.com", "pass", Role.EMPLOYEE);
        empB = createUser("Employee B", "empb@pen.com", "pass", Role.EMPLOYEE);

        projectA = createProject("Project A", "Desc A", managerA, ProjectStatus.IN_PROGRESS);
        projectB = createProject("Project B", "Desc B", managerB, ProjectStatus.IN_PROGRESS);

        taskA = createTask("Task A", "Desc", projectA, empA, TaskStatus.IN_PROGRESS);
        taskB = createTask("Task B", "Desc", projectB, empB, TaskStatus.IN_PROGRESS);

        submissionA = createSubmission(taskA, empA, "Report A", "linkA", SubmissionStatus.UNDER_REVIEW);
        submissionB = createSubmission(taskB, empB, "Report B", "linkB", SubmissionStatus.UNDER_REVIEW);

        notifB = createNotification(empB, "Notif B", "Msg B", NotificationType.TASK_ASSIGNED);
        commentB = createComment(taskB, empB, "Comment by B");
    }

    // =========================================================
    // HORIZONTAL AUTHORIZATION (EMPLOYEE TO EMPLOYEE)
    // =========================================================

    @Test
    @DisplayName("PenTest: Employee A -> Employee B Task (GET /api/tasks/{id}) returns 403")
    void testEmpA_CannotAccess_EmpB_Task() throws Exception {
        mockMvc.perform(get("/api/tasks/" + taskB.getId()).cookie(createAuthCookie(empA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PenTest: Employee A -> Employee B Task list (GET /api/tasks/employee/{id}) returns 403")
    void testEmpA_CannotAccess_EmpB_TaskList() throws Exception {
        mockMvc.perform(get("/api/tasks/employee/" + empB.getId()).cookie(createAuthCookie(empA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PenTest: Employee A -> Employee B Submission (GET /api/submissions/{id}) returns 403")
    void testEmpA_CannotAccess_EmpB_Submission() throws Exception {
        mockMvc.perform(get("/api/submissions/" + submissionB.getId()).cookie(createAuthCookie(empA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PenTest: Employee A -> Employee B Notifications (PATCH /api/notifications/{id}/read) returns 403")
    void testEmpA_CannotMutate_EmpB_Notification() throws Exception {
        mockMvc.perform(patch("/api/notifications/" + notifB.getId() + "/read").cookie(createAuthCookie(empA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PenTest: Employee A -> Employee B Comment (PUT /api/comments/{id}) returns 403")
    void testEmpA_CannotMutate_EmpB_Comment() throws Exception {
        CreateCommentRequest req = new CreateCommentRequest("Tampered comment");
        mockMvc.perform(put("/api/comments/" + commentB.getId())
                        .cookie(createAuthCookie(empA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================
    // HORIZONTAL AUTHORIZATION (MANAGER TO MANAGER)
    // =========================================================

    @Test
    @DisplayName("PenTest: Manager A -> Manager B Project (PUT /api/projects/{id}) returns 403")
    void testMgrA_CannotMutate_MgrB_Project() throws Exception {
        Project update = new Project();
        update.setName("Hacked Project B");
        mockMvc.perform(put("/api/projects/" + projectB.getId())
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PenTest: Manager A -> Manager B Task (DELETE /api/tasks/{id}) returns 403")
    void testMgrA_CannotDelete_MgrB_Task() throws Exception {
        mockMvc.perform(delete("/api/tasks/" + taskB.getId()).cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PenTest: Manager A -> Manager B Submission (PATCH /api/submissions/{id}/approve) returns 403")
    void testMgrA_CannotApprove_MgrB_Submission() throws Exception {
        mockMvc.perform(patch("/api/submissions/" + submissionB.getId() + "/approve").cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================
    // VERTICAL AUTHORIZATION (EMPLOYEE / MANAGER TO HIGHER PRIVILEGES)
    // =========================================================

    @Test
    @DisplayName("PenTest: Employee -> Manager Dashboard (GET /api/dashboard/manager) returns 403")
    void testEmployee_CannotAccess_ManagerDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/manager").cookie(createAuthCookie(empA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PenTest: Employee -> Admin Endpoint (GET /api/users/admin) returns 403")
    void testEmployee_CannotAccess_AdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/admin").cookie(createAuthCookie(empA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PenTest: Manager -> Admin Endpoint (GET /api/users/admin) returns 403")
    void testManager_CannotAccess_AdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/admin").cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PenTest: Unauthenticated -> Protected Endpoint (GET /api/projects) returns 401/403")
    void testUnauthenticated_CannotAccess_ProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isForbidden());
    }
}
