package com.Workmanagement.integration;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SubmissionApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/submissions - Employee submits work for assigned task")
    void testCreateSubmissionSuccess() throws Exception {
        User manager = createUser("Manager", "mgr@sub.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@sub.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Mobile App", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("API integration", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        Submission payload = new Submission();
        payload.setReport("Implemented all REST endpoints with JWT authentication.");
        payload.setGithubUrl("https://github.com/repo/pull/1");

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.report").value("Implemented all REST endpoints with JWT authentication."))
                .andExpect(jsonPath("$.submittedBy.id").value(emp.getId()))
                .andExpect(jsonPath("$.task.id").value(task.getId()));

        assertEquals(1, submissionRepository.count());
    }

    @Test
    @DisplayName("POST /api/submissions - Employee cannot submit work for unassigned task (403 FORBIDDEN)")
    void testCreateSubmissionUnassignedTaskForbidden() throws Exception {
        User manager = createUser("Manager", "mgr@sub.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@sub.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@sub.com", "pass", Role.EMPLOYEE);

        Project project = createProject("Web App", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Emp 1's Task", "Desc", project, emp1, TaskStatus.IN_PROGRESS);

        Submission payload = new Submission();
        payload.setReport("Trying to submit on Emp 1 task");

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(createAuthCookie(emp2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Complete Lifecycle: SUBMITTED -> UNDER_REVIEW -> APPROVED -> Task COMPLETED")
    void testCompleteSubmissionApprovalWorkflow() throws Exception {
        User manager = createUser("Manager", "mgr@sub.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@sub.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Web App", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task Workflow", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        Submission submission = createSubmission(task, emp, "Completed task features", "https://pr.link", SubmissionStatus.UNDER_REVIEW);

        // Manager approves submission
        mockMvc.perform(patch("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify in DB that submission is APPROVED and task is COMPLETED
        Submission updatedSub = submissionRepository.findById(submission.getId()).orElse(null);
        assertNotNull(updatedSub);
        assertEquals(SubmissionStatus.APPROVED, updatedSub.getStatus());

        Task updatedTask = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(updatedTask);
        assertEquals(TaskStatus.COMPLETED, updatedTask.getStatus());
    }

    @Test
    @DisplayName("Changes Requested Workflow: UNDER_REVIEW -> CHANGES_REQUESTED -> Task IN_PROGRESS -> Resubmit -> SUBMITTED")
    void testChangesRequestedAndResubmitWorkflow() throws Exception {
        User manager = createUser("Manager", "mgr@sub.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@sub.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task Alpha", "Desc", project, emp, TaskStatus.UNDER_REVIEW);

        Submission submission = createSubmission(task, emp, "Initial report", "link", SubmissionStatus.UNDER_REVIEW);

        // 1. Manager requests changes with required feedback
        com.Workmanagement.submission.dto.ReviewSubmissionRequest reviewReq = new com.Workmanagement.submission.dto.ReviewSubmissionRequest();
        reviewReq.setFeedback("Please update the API documentation and test edge cases.");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.managerFeedback").value("Please update the API documentation and test edge cases."));

        // Verify task transitioned to CHANGES_REQUESTED
        Task taskAfterReq = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(taskAfterReq);
        assertEquals(TaskStatus.CHANGES_REQUESTED, taskAfterReq.getStatus());

        // 2. Employee resubmits work
        Submission resubmissionPayload = new Submission();
        resubmissionPayload.setReport("Revised work addressing review comments.");
        resubmissionPayload.setGithubUrl("https://pr.link/v2");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/resubmit")
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resubmissionPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.report").value("Revised work addressing review comments."));

        assertEquals(2, submissionRepository.count());
    }

    @Test
    @DisplayName("GET /api/submissions/me - Employee retrieves strictly their own submissions")
    void testGetMySubmissions() throws Exception {
        User manager = createUser("Manager", "mgr@sub.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@sub.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@sub.com", "pass", Role.EMPLOYEE);

        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task1 = createTask("Task 1", "Desc", project, emp1, TaskStatus.IN_PROGRESS);
        Task task2 = createTask("Task 2", "Desc", project, emp2, TaskStatus.IN_PROGRESS);

        createSubmission(task1, emp1, "Emp 1 sub", "link1", SubmissionStatus.SUBMITTED);
        createSubmission(task2, emp2, "Emp 2 sub", "link2", SubmissionStatus.SUBMITTED);

        mockMvc.perform(get("/api/submissions/me").cookie(createAuthCookie(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].submittedBy.id").value(emp1.getId()))
                .andExpect(jsonPath("$[0].report").value("Emp 1 sub"));
    }

    @Test
    @DisplayName("PATCH /api/submissions/{id}/approve - Manager A cannot approve Manager B's submission (403 FORBIDDEN)")
    void testManagerSubmissionReviewIsolation() throws Exception {
        User managerA = createUser("Manager A", "mgra@sub.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@sub.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@sub.com", "pass", Role.EMPLOYEE);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", "Desc", projectB, emp, TaskStatus.UNDER_REVIEW);
        Submission subB = createSubmission(taskB, emp, "Report B", "link", SubmissionStatus.UNDER_REVIEW);

        mockMvc.perform(patch("/api/submissions/" + subB.getId() + "/approve")
                        .cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
