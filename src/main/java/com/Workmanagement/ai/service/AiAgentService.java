package com.Workmanagement.ai.service;

import com.Workmanagement.ai.tools.ProjectTools;
import com.Workmanagement.ai.tools.SubmissionTools;
import com.Workmanagement.ai.tools.TaskTools;
import com.Workmanagement.ai.tools.UserTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    private final ChatClient chatClient;

    public AiAgentService(
            ChatClient.Builder chatClientBuilder,
            TaskTools taskTools,
            SubmissionTools submissionTools,
            ProjectTools projectTools,
            UserTools userTools
    ) {

        ToolCallback[] tools =
                MethodToolCallbackProvider
                        .builder()
                        .toolObjects(
                                taskTools,
                                submissionTools,
                                projectTools,
                                userTools
                        )
                        .build()
                        .getToolCallbacks();

        log.info("AI Agent registered {} read-only tools", tools.length);

        this.chatClient = chatClientBuilder
                .defaultSystem("""
                    You are an AI work management assistant for managers.

                    Help managers understand their projects,
                    tasks, employees, submissions, deadlines,
                    and work progress.

                    You have access to tools that retrieve
                    real information from the database:
                    - getTask(taskId), getEmployeeTasks(employeeId),
                      getOverdueTasks(), getProjectTasks(projectId)
                    - getSubmission(submissionId), getTaskSubmissions(taskId),
                      getEmployeeSubmissions(employeeId)
                    - getProject(projectId), getAllProjects()
                    - getEmployeeById(userId), getEmployeeByEmail(email),
                      getAllEmployees(), getEmployeesByRole(role)

                    Rules:
                    - ALWAYS use tools whenever database information is required.
                    - Never invent task, project, employee, or submission data.
                    - If a tool reports "not found", clearly say the record does not exist.
                    - If you need an employee ID but only have a name, use getAllEmployees()
                      or getEmployeesByRole('EMPLOYEE') first to find the matching user,
                      then use their userId with the other tools.
                    - Keep answers concise and useful.
                    - Do not modify database data.
                    - You are currently a read-only assistant.
                    """)
                .defaultTools(tools)
                .build();
    }

    public String chat(String message) {

        return chatClient
                .prompt()
                .user(message)
                .call()
                .content();
    }
}
