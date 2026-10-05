package com.Workmanagement.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health Check", description = "Endpoints for server health check, uptime, and system diagnostics")
@RestController
public class HealthController {

    @Value("${spring.application.name:Work-management}")
    private String applicationName;

    @Value("${info.app.version:1.0.0}")
    private String applicationVersion;

    private final DataSource dataSource;

    @Autowired
    public HealthController(@Autowired(required = false) DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Operation(summary = "Comprehensive Health Check", description = "Returns server status, uptime, memory metrics, and database connectivity.")
    @GetMapping({"/healthcheck", "/health", "/api/health", "/api/healthcheck"})
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new LinkedHashMap<>();
        Instant now = Instant.now();

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = runtimeBean.getUptime();
        long uptimeSeconds = uptimeMillis / 1000;

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        // Check Database Connectivity
        boolean dbHealthy = false;
        String dbDetails = "Database connection not configured";
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(2)) {
                    dbHealthy = true;
                    dbDetails = "Connected: " + connection.getMetaData().getDatabaseProductName() + " " + connection.getMetaData().getDatabaseProductVersion();
                } else {
                    dbDetails = "Validation timeout / connection invalid";
                }
            } catch (Exception e) {
                dbHealthy = false;
                dbDetails = "Connection failed: " + e.getMessage();
            }
        }

        String overallStatus = (dataSource == null || dbHealthy) ? "UP" : "DEGRADED";

        response.put("status", overallStatus);
        response.put("service", applicationName);
        response.put("version", applicationVersion);
        response.put("timestamp", now.toString());
        response.put("uptimeSeconds", uptimeSeconds);
        response.put("uptimeFormatted", formatUptime(uptimeSeconds));

        // Database info
        Map<String, Object> dbInfo = new LinkedHashMap<>();
        dbInfo.put("status", dbHealthy ? "UP" : "DOWN");
        dbInfo.put("details", dbDetails);
        response.put("database", dbInfo);

        // System & JVM info
        Map<String, Object> systemInfo = new LinkedHashMap<>();
        systemInfo.put("javaVersion", System.getProperty("java.version"));
        systemInfo.put("os", System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        systemInfo.put("availableProcessors", runtime.availableProcessors());
        systemInfo.put("usedMemoryMB", usedMemory);
        systemInfo.put("freeMemoryMB", freeMemory);
        systemInfo.put("totalMemoryMB", totalMemory);
        systemInfo.put("maxMemoryMB", maxMemory);
        response.put("system", systemInfo);

        HttpStatus httpStatus = "UP".equals(overallStatus) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseEntity<>(response, httpStatus);
    }

    @Operation(summary = "Liveness / Ping Check", description = "Lightweight ping endpoint returning immediate server status.")
    @GetMapping({"/healthcheck/live", "/health/live", "/ping"})
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("pong", true);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    private String formatUptime(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString();
    }
}
