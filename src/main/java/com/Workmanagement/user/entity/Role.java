package com.Workmanagement.user.entity;

public enum Role {
    ADMIN,           // manages -> users, managers, projects. monitor-> organization

    MANAGER,         // create tasks, assign employees, review submission, approve/request changes

    EMPLOYEE         // view tasks, update progress, submit work, upload evidence, resubmit after changes
}
