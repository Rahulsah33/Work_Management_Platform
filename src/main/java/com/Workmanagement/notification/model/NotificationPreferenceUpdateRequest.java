package com.Workmanagement.notification.model;

import jakarta.validation.constraints.NotNull;

public class NotificationPreferenceUpdateRequest {

    @NotNull(message = "enabled field is required")
    private Boolean enabled;

    public NotificationPreferenceUpdateRequest() {
    }

    public NotificationPreferenceUpdateRequest(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
