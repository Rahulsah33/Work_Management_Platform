package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.auth.dto.LoginRequest;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class EmployeeSubmissionWorkflowIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Test 1: Login as Employee → PASS: Authenticates employee and issues JWT cookie")
    void test1_EmployeeLogin() throws Exception {
        User employee = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("elena@company.com");
        loginRequest.setPassword("EmpPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.userId").value(employee.getId()))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    @DisplayName("Test 2: Retrieve assigned tasks → PASS: Employee gets assigned task list")
    void test2_RetrieveAssignedTasks() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Payment Gateway", "Stripe API Integration", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Webhook Handler", "Process invoice payments", project, emp, TaskStatus.IN_PROGRESS);

        Cookie authCookie = createAuthCookie(emp);

        mockMvc.perform(get("/api/tasks/my").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(task.getId()))
                .andExpect(jsonPath("$[0].title").value("Webhook Handler"));
    }

    @Test
    @DisplayName("Test 3: Submit work for assigned task → 200 OK & Status = SUBMITTED")
    void test3_SubmitWorkSuccess() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Payment Gateway", "Stripe API Integration", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Webhook Handler", "Process invoice payments", project, emp, TaskStatus.IN_PROGRESS);

        Submission submission = new Submission();
        submission.setReport("Implemented Stripe webhook verification with HMAC SHA256 and idempotency keys.");
        submission.setGithubUrl("https://github.com/company/payments/pull/42");
        submission.setEvidenceUrl("https://staging.payments.company.com/webhook-test");

        Cookie authCookie = createAuthCookie(emp);

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.report").value("Implemented Stripe webhook verification with HMAC SHA256 and idempotency keys."))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/company/payments/pull/42"))
                .andExpect(jsonPath("$.evidenceUrl").value("https://staging.payments.company.com/webhook-test"))
                .andExpect(jsonPath("$.submittedBy.id").value(emp.getId()))
                .andExpect(jsonPath("$.task.id").value(task.getId()));

        // Verify task status transitioned to SUBMITTED
        Task updatedTask = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(updatedTask);
        assertEquals(TaskStatus.SUBMITTED, updatedTask.getStatus());
    }

    @Test
    @DisplayName("Test 4: Verify submission exists in database → PASS")
    void test4_VerifySubmissionInDatabase() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Mobile Core", "Mobile app backend", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Push Notifications", "FCM setup", project, emp, TaskStatus.IN_PROGRESS);

        Submission submission = new Submission();
        submission.setReport("Integrated Firebase Cloud Messaging SDK and tested APNS token rotation.");

        Cookie authCookie = createAuthCookie(emp);

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isOk());

        assertEquals(1, submissionRepository.count());
        Submission saved = submissionRepository.findAll().get(0);
        assertEquals(SubmissionStatus.SUBMITTED, saved.getStatus());
        assertEquals(emp.getId(), saved.getSubmittedBy().getId());
        assertEquals(task.getId(), saved.getTask().getId());
        assertNotNull(saved.getSubmittedAt());
    }

    @Test
    @DisplayName("Test 5: Employee attempts to submit for another employee's task → 403 Forbidden")
    void test5_EmployeeCannotSubmitAnotherEmployeesTask() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User empA = createUser("Employee A", "empa@company.com", "EmpPass123!", Role.EMPLOYEE);
        User empB = createUser("Employee B", "empb@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Security Platform", "Auth service", manager, ProjectStatus.IN_PROGRESS);
        Task taskA = createTask("Task A", "Emp A task", project, empA, TaskStatus.IN_PROGRESS);

        Submission submission = new Submission();
        submission.setReport("Employee B trying to submit work on Employee A's task");

        Cookie authCookieEmpB = createAuthCookie(empB);

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", taskA.getId().toString())
                        .cookie(authCookieEmpB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        assertEquals(0, submissionRepository.count());
    }

    @Test
    @DisplayName("Test 6: Employee attempts blank submission → 400 Bad Request")
    void test6_BlankSubmissionRejected() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Portal", "Web Portal", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("UI Components", "Build form controls", project, emp, TaskStatus.IN_PROGRESS);

        Submission blankSubmission = new Submission();
        blankSubmission.setReport("   ");

        Cookie authCookie = createAuthCookie(emp);

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankSubmission)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Submission report cannot be empty")));
    }

    @Test
    @DisplayName("Test 7: Employee attempts submission with invalid task ID → 404 Not Found")
    void test7_InvalidTaskIdRejected() throws Exception {
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Submission submission = new Submission();
        submission.setReport("Valid report content for non-existent task");

        Cookie authCookie = createAuthCookie(emp);

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", "99999")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Test 8: Test duplicate submission according to existing business rules → 400 Bad Request")
    void test8_DuplicateActiveSubmissionPrevented() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Payment Engine", "Core engine", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Billing Logic", "Calculate taxes", project, emp, TaskStatus.IN_PROGRESS);

        createSubmission(task, emp, "First submission", "link1", SubmissionStatus.SUBMITTED);

        Submission duplicateSub = new Submission();
        duplicateSub.setReport("Second submission while first is still SUBMITTED");

        Cookie authCookie = createAuthCookie(emp);

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateSub)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("active submission under review")));
    }

    @Test
    @DisplayName("Test 9: MANAGER existing submission access & review actions still work → PASS")
    void test9_ManagerSubmissionAccessAndReview() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Dashboard App", "Analytics client", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Chart Module", "ChartJS integration", project, emp, TaskStatus.IN_PROGRESS);

        Submission submission = createSubmission(task, emp, "Finished charting components", "https://github.com/pr/5", SubmissionStatus.UNDER_REVIEW);

        Cookie managerCookie = createAuthCookie(manager);

        // Manager queries submission by task
        mockMvc.perform(get("/api/submissions/task/" + task.getId()).cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(submission.getId()));

        // Manager approves submission
        mockMvc.perform(patch("/api/submissions/" + submission.getId() + "/approve").cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Task completedTask = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(completedTask);
        assertEquals(TaskStatus.COMPLETED, completedTask.getStatus());
    }

    @Test
    @DisplayName("Test 10: Existing AI evaluation endpoints remain functional → PASS")
    void test10_AiEvaluationEndpointsRemainFunctional() throws Exception {
        User manager = createUser("Manager One", "mgr@company.com", "MgrPass123!", Role.MANAGER);
        User emp = createUser("Elena Dev", "elena@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("AI Core", "Neural nets", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Inference Pipeline", "FastAPI server", project, emp, TaskStatus.IN_PROGRESS);

        Submission submission = createSubmission(task, emp, "Implemented ONNX runtime inference", "link", SubmissionStatus.SUBMITTED);
        AiEvaluation eval = createAiEvaluation(submission, 95.0, 92.0, AiEvaluationStatus.COMPLETED);

        Cookie empCookie = createAuthCookie(emp);

        mockMvc.perform(get("/api/ai/evaluations/submission/" + submission.getId()).cookie(empCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qualityScore").value(92.0))
                .andExpect(jsonPath("$.completionPercentage").value(95.0));
    }
}
