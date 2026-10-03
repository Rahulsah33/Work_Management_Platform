package com.Workmanagement.integration;

import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.ai.service.AiEvaluationService;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.service.SubmissionService;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.service.TaskService;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone7Step2NotificationEventTriggersIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private AiEvaluationService aiEvaluationService;

    private AiEvaluationResult createMockResult(Double completion, Double quality, Double confidence, String feedback) {
        AiEvaluationResult result = new AiEvaluationResult();
        result.setCompletionPercentage(completion);
        result.setQualityScore(quality);
        result.setConfidenceScore(confidence);
        result.setFeedback(feedback);
        result.setRequirements(Collections.emptyList());
        return result;
    }

    @Test
    @DisplayName("Test 1: Task Assigned - Employee receives TASK_ASSIGNED notification")
    void test1_taskAssignedNotification() throws Exception {
        User manager = createUser("Manager M", "manager_t1@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E", "emp_t1@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Portal Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);

        Task task = new Task();
        task.setTitle("Design DB Schema");
        task.setDescription("Create PostgreSQL schemas");
        task.setPriority("HIGH");
        task.setEndDate(LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", project.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isOk());

        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, empNotifs.size());
        assertEquals(NotificationType.TASK_ASSIGNED, empNotifs.get(0).getType());
        assertEquals("New Task Assigned", empNotifs.get(0).getTitle());
        assertTrue(empNotifs.get(0).getMessage().contains("Design DB Schema"));
        assertEquals("TASK", empNotifs.get(0).getRelatedType());
    }

    @Test
    @DisplayName("Test 2: Task Assignment Failure - No TASK_ASSIGNED notification created on failure")
    void test2_taskAssignmentFailureNoNotification() throws Exception {
        User manager = createUser("Manager M2", "manager_t2@test.com", "pass123", Role.MANAGER);
        User nonEmployee = createUser("Other Manager", "other_m2@test.com", "pass123", Role.MANAGER);
        Project project = createProject("Portal Proj 2", "Desc", manager, ProjectStatus.IN_PROGRESS);

        Task task = new Task();
        task.setTitle("Invalid Assign Task");
        task.setPriority("HIGH");

        // Attempt to assign task to non-employee -> returns 400 Bad Request
        mockMvc.perform(post("/api/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", project.getId().toString())
                        .param("employeeId", nonEmployee.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isBadRequest());

        assertEquals(0, notificationRepository.count());
    }

    @Test
    @DisplayName("Test 3: Submission Received - Manager receives SUBMISSION_RECEIVED notification")
    void test3_submissionReceivedNotification() throws Exception {
        User manager = createUser("Manager M3", "manager_t3@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E3", "emp_t3@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Proj 3", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Build Auth", "Auth API", project, employee, TaskStatus.ASSIGNED);

        Submission sub = new Submission();
        sub.setReport("Completed JWT authentication with unit tests");
        sub.setGithubUrl("https://github.com/test/repo");

        mockMvc.perform(post("/api/submissions")
                        .cookie(createAuthCookie(employee))
                        .param("taskId", task.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sub)))
                .andExpect(status().isOk());

        List<Notification> managerNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertEquals(1, managerNotifs.size());
        assertEquals(NotificationType.SUBMISSION_RECEIVED, managerNotifs.get(0).getType());
        assertEquals("New Submission Received", managerNotifs.get(0).getTitle());
        assertTrue(managerNotifs.get(0).getMessage().contains("Build Auth"));
        assertEquals("SUBMISSION", managerNotifs.get(0).getRelatedType());
    }

    @Test
    @DisplayName("Test 4: AI Evaluation Success - Manager receives AI_EVALUATION_READY notification")
    void test4_aiEvaluationSuccessNotification() throws Exception {
        User manager = createUser("Manager M4", "manager_t4@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E4", "emp_t4@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Proj 4", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Frontend UI", "Build UI", project, employee, TaskStatus.SUBMITTED);
        createRequirement(task, "Implement navbar", 10, true);
        Submission submission = createSubmission(task, employee, "UI implementation finished", "https://github.com/ui", SubmissionStatus.SUBMITTED);

        AiEvaluationResult mockResult = createMockResult(95.0, 90.0, 92.0, "Great UI implementation");
        Mockito.when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(mockResult);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk());

        List<Notification> managerNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertTrue(managerNotifs.stream().anyMatch(n -> n.getType() == NotificationType.AI_EVALUATION_READY));
        Notification evalNotif = managerNotifs.stream().filter(n -> n.getType() == NotificationType.AI_EVALUATION_READY).findFirst().orElseThrow();
        assertEquals("AI Evaluation Ready", evalNotif.getTitle());
        assertTrue(evalNotif.getMessage().contains("Frontend UI"));
        assertEquals("SUBMISSION", evalNotif.getRelatedType());
    }

    @Test
    @DisplayName("Test 5: AI Evaluation Failure - No AI_EVALUATION_READY notification on error")
    void test5_aiEvaluationFailureNoNotification() throws Exception {
        User manager = createUser("Manager M5", "manager_t5@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E5", "emp_t5@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Proj 5", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 5", "Desc", project, employee, TaskStatus.ASSIGNED);
        // Submission is already in UNDER_REVIEW status, not SUBMITTED -> evaluate will fail with 400
        Submission submission = createSubmission(task, employee, "Report", "url", SubmissionStatus.UNDER_REVIEW);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isBadRequest());

        List<Notification> managerNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertEquals(0, managerNotifs.size());
    }

    @Test
    @DisplayName("Test 6: Changes Requested - Employee receives CHANGES_REQUESTED notification")
    void test6_changesRequestedNotification() throws Exception {
        User manager = createUser("Manager M6", "manager_t6@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E6", "emp_t6@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Proj 6", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Backend API", "API specs", project, employee, TaskStatus.UNDER_REVIEW);
        Submission submission = createSubmission(task, employee, "Draft API", "url", SubmissionStatus.UNDER_REVIEW);

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType("application/json")
                        .content("{\"feedback\":\"Please add unit tests\"}"))
                .andExpect(status().isOk());

        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, empNotifs.size());
        assertEquals(NotificationType.CHANGES_REQUESTED, empNotifs.get(0).getType());
        assertEquals("Changes Requested", empNotifs.get(0).getTitle());
        assertTrue(empNotifs.get(0).getMessage().contains("Backend API"));
        assertEquals("SUBMISSION", empNotifs.get(0).getRelatedType());
    }

    @Test
    @DisplayName("Test 7: Submission Approved - Employee receives SUBMISSION_APPROVED notification")
    void test7_submissionApprovedNotification() throws Exception {
        User manager = createUser("Manager M7", "manager_t7@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E7", "emp_t7@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Proj 7", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Deploy Pipeline", "CI/CD setup", project, employee, TaskStatus.UNDER_REVIEW);
        Submission submission = createSubmission(task, employee, "Pipeline configured with GitHub Actions", "url", SubmissionStatus.UNDER_REVIEW);

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType("application/json")
                        .content("{\"feedback\":\"Excellent deployment pipeline\"}"))
                .andExpect(status().isOk());

        List<Notification> empNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId());
        assertEquals(1, empNotifs.size());
        assertEquals(NotificationType.SUBMISSION_APPROVED, empNotifs.get(0).getType());
        assertEquals("Submission Approved", empNotifs.get(0).getTitle());
        assertTrue(empNotifs.get(0).getMessage().contains("Deploy Pipeline"));
        assertEquals("SUBMISSION", empNotifs.get(0).getRelatedType());
    }

    @Test
    @DisplayName("Test 8: Resubmission - Manager receives SUBMISSION_RECEIVED notification for revision")
    void test8_resubmissionNotification() throws Exception {
        User manager = createUser("Manager M8", "manager_t8@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E8", "emp_t8@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Proj 8", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Bug Fixing", "Fix NPE", project, employee, TaskStatus.CHANGES_REQUESTED);
        Submission v1 = createSubmission(task, employee, "Initial patch", "url", SubmissionStatus.CHANGES_REQUESTED);
        v1.setVersion(1);
        submissionRepository.save(v1);

        Submission v2 = new Submission();
        v2.setReport("Revised patch with null checks and unit tests");
        v2.setGithubUrl("https://github.com/patch-v2");

        mockMvc.perform(post("/api/submissions/" + v1.getId() + "/resubmit")
                        .cookie(createAuthCookie(employee))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(v2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        List<Notification> managerNotifs = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(manager.getId());
        assertEquals(1, managerNotifs.size());
        assertEquals(NotificationType.SUBMISSION_RECEIVED, managerNotifs.get(0).getType());
        assertTrue(managerNotifs.get(0).getMessage().contains("v2"));
    }

    @Test
    @DisplayName("Test 9: Correct Recipient - Notifications strictly delivered to associated users")
    void test9_correctRecipientEnforcement() throws Exception {
        User managerA = createUser("Manager A", "mgr_a@test.com", "pass123", Role.MANAGER);
        User managerB = createUser("Manager B", "mgr_b@test.com", "pass123", Role.MANAGER);
        User employeeA = createUser("Employee A", "emp_a@test.com", "pass123", Role.EMPLOYEE);
        User employeeB = createUser("Employee B", "emp_b@test.com", "pass123", Role.EMPLOYEE);

        Project projectA = createProject("Project A", "Desc", managerA, ProjectStatus.IN_PROGRESS);
        Task taskA = createTask("Task A", "Desc", projectA, employeeA, TaskStatus.ASSIGNED);

        Submission sub = new Submission();
        sub.setReport("Employee A Report");

        mockMvc.perform(post("/api/submissions")
                        .cookie(createAuthCookie(employeeA))
                        .param("taskId", taskA.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sub)))
                .andExpect(status().isOk());

        // Manager A receives notification
        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(managerA.getId()).size());
        // Unrelated Manager B receives 0 notifications
        assertEquals(0, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(managerB.getId()).size());
        // Employee B receives 0 notifications
        assertEquals(0, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employeeB.getId()).size());
    }

    @Test
    @DisplayName("Test 10: Duplicate Protection - Idempotent operations do not duplicate notifications")
    void test10_duplicateProtection() throws Exception {
        User manager = createUser("Manager M10", "manager_t10@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee E10", "emp_t10@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Proj 10", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 10", "Desc", project, employee, TaskStatus.UNDER_REVIEW);
        Submission submission = createSubmission(task, employee, "Report", "url", SubmissionStatus.UNDER_REVIEW);

        // First approval succeeds -> 1 notification
        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk());

        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId()).size());

        // Repeated approval is rejected by workflow (400 Bad Request)
        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isBadRequest());

        // Count remains exactly 1
        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(employee.getId()).size());
    }

    @Test
    @DisplayName("Test 11: Transaction Rollback - Failing operation rolls back any notifications")
    void test11_transactionRollbackNoOrphanNotifications() {
        User employee = createUser("Employee E11", "emp_t11@test.com", "pass123", Role.EMPLOYEE);

        try {
            // Task creation with non-existent project will fail and rollback transaction
            taskService.createTask(new Task(), 99999L, employee.getId());
        } catch (Exception e) {
            // Expected exception
        }

        assertEquals(0, notificationRepository.count());
    }

    @Test
    @DisplayName("Test 12: Complete End-to-End Notification Event Trigger Lifecycle")
    void test12_completeLifecycleNotificationTriggers() throws Exception {
        User manager = createUser("Manager EndToEnd", "m_e2e@test.com", "pass123", Role.MANAGER);
        User employee = createUser("Employee EndToEnd", "e_e2e@test.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("E2E Portal", "Desc", manager, ProjectStatus.IN_PROGRESS);

        // 1. Task Assigned
        Task task = new Task();
        task.setTitle("E2E Feature");
        task.setDescription("Feature details");
        task.setPriority("HIGH");
        task.setEndDate(LocalDate.now().plusDays(7));

        mockMvc.perform(post("/api/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", project.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isOk());

        Task createdTask = taskRepository.findByAssignedTo(employee).get(0);
        createRequirement(createdTask, "Req 1", 10, true);

        // Employee has 1 unread notification (TASK_ASSIGNED)
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // 2. Submission 1
        Submission sub1 = new Submission();
        sub1.setReport("Submission v1 draft");
        mockMvc.perform(post("/api/submissions")
                        .cookie(createAuthCookie(employee))
                        .param("taskId", createdTask.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sub1)))
                .andExpect(status().isOk());

        Submission savedSub1 = submissionRepository.findByTaskIdOrderByVersionDesc(createdTask.getId()).get(0);

        // Manager receives SUBMISSION_RECEIVED
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // 3. AI Evaluation
        AiEvaluationResult mockResult1 = createMockResult(70.0, 65.0, 80.0, "Missing tests");
        Mockito.when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(mockResult1);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + savedSub1.getId()).cookie(createAuthCookie(manager)))
                .andExpect(status().isOk());

        // Manager receives AI_EVALUATION_READY (count = 2)
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        // 4. Manager requests changes
        mockMvc.perform(post("/api/submissions/" + savedSub1.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType("application/json")
                        .content("{\"feedback\":\"Add test cases\"}"))
                .andExpect(status().isOk());

        // Employee receives CHANGES_REQUESTED (count = 2)
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        // 5. Resubmission v2
        Submission sub2 = new Submission();
        sub2.setReport("Submission v2 with test cases included");
        mockMvc.perform(post("/api/submissions/" + savedSub1.getId() + "/resubmit")
                        .cookie(createAuthCookie(employee))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sub2)))
                .andExpect(status().isOk());

        Submission savedSub2 = submissionRepository.findByTaskIdOrderByVersionDesc(createdTask.getId()).get(0);

        // Manager receives SUBMISSION_RECEIVED for v2 (count = 3)
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        // 6. AI Evaluation v2
        AiEvaluationResult mockResult2 = createMockResult(100.0, 95.0, 95.0, "Excellent submission");
        Mockito.when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(mockResult2);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + savedSub2.getId()).cookie(createAuthCookie(manager)))
                .andExpect(status().isOk());

        // Manager receives AI_EVALUATION_READY (count = 4)
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));

        // 7. Manager Approves
        mockMvc.perform(post("/api/submissions/" + savedSub2.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType("application/json")
                        .content("{\"feedback\":\"Approved, stellar work!\"}"))
                .andExpect(status().isOk());

        // Employee receives SUBMISSION_APPROVED (count = 3: TASK_ASSIGNED + CHANGES_REQUESTED + SUBMISSION_APPROVED)
        mockMvc.perform(get("/api/notifications/unread/count").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        // Employee retrieves notifications
        mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type").value("SUBMISSION_APPROVED"))
                .andExpect(jsonPath("$[1].type").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$[2].type").value("TASK_ASSIGNED"));
    }
}
