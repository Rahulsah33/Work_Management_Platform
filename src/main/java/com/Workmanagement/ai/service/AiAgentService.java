package com.Workmanagement.ai.service;

import com.Workmanagement.ai.tools.ProjectTools;
import com.Workmanagement.ai.tools.SubmissionTools;
import com.Workmanagement.ai.tools.TaskTools;
import com.Workmanagement.ai.tools.UserTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class AiAgentService {

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

        this.chatClient = chatClientBuilder
                .defaultOptions(
                        GoogleGenAiChatOptions.builder()
                                .responseMimeType("text/plain")
                )
                .defaultSystem("""
                    You are an AI work management assistant.

                    Help managers understand their projects,
                    tasks, employees, users, submissions, deadlines,
                    and work progress.

                    You have access to tools that retrieve
                    real information from the database.

                    Rules:
                    - Use tools whenever database information is required.
                    - Use tools whenever employee or user information is required.
                    - Never invent task, project, employee, user, or submission data.
                    - Never expose passwords, JWTs, authentication credentials, or secrets.
                    - If information cannot be found, clearly say so.
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