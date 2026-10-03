package com.Workmanagement.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AppInfoConfig implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("app", Map.of(
                "name", "AI-Powered Work Management Platform",
                "version", "1.0.0",
                "description", "AI-Powered Work Management System Backend API"
        ));
    }
}
