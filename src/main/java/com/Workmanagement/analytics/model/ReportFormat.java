package com.Workmanagement.analytics.model;

public enum ReportFormat {
    CSV,
    EXCEL,
    PDF;

    public static ReportFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return CSV;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "EXCEL", "XLSX", "XLS" -> EXCEL;
            case "PDF" -> PDF;
            case "CSV" -> CSV;
            default -> throw new IllegalArgumentException("Unsupported report format: " + value + ". Supported formats are CSV, EXCEL, and PDF.");
        };
    }
}
