package com.Workmanagement.analytics.model;

public record ReportFile(
        byte[] content,
        String filename,
        String contentType
) {
}
