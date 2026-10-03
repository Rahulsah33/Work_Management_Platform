package com.Workmanagement.auth.security;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    public boolean canManageProject(Project project, User user) {
        if (user == null || project == null) return false;
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER) return true;
        return false;
    }

    public void checkManageProject(Project project, User user) {
        if (!canManageProject(project, user)) {
            throw new AccessDeniedException("You are not authorized to manage this project");
        }
    }

    public boolean canAccessProject(Project project, User user) {
        if (user == null || project == null) return false;
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER || user.getRole() == Role.EMPLOYEE) return true;
        return false;
    }

    public void checkAccessProject(Project project, User user) {
        if (!canAccessProject(project, user)) {
            throw new AccessDeniedException("You are not authorized to access this project");
        }
    }

    public boolean canManageTask(Task task, User user) {
        if (user == null || task == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() == Role.MANAGER) {
            return task.getProject() == null || canManageProject(task.getProject(), user);
        }
        return false;
    }

    public void checkManageTask(Task task, User user) {
        if (!canManageTask(task, user)) {
            throw new AccessDeniedException("You are not authorized to manage this task");
        }
    }

    public boolean canAccessTask(Task task, User user) {
        if (user == null || task == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() == Role.MANAGER) {
            return task.getProject() == null || canManageProject(task.getProject(), user);
        }
        if (user.getRole() == Role.EMPLOYEE) {
            return task.getAssignedTo() != null && user.getId().equals(task.getAssignedTo().getId());
        }
        return false;
    }

    public void checkAccessTask(Task task, User user) {
        if (!canAccessTask(task, user)) {
            throw new AccessDeniedException("You are not authorized to access this task");
        }
    }

    public boolean canManageSubmission(Submission submission, User user) {
        if (user == null || submission == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() == Role.MANAGER) {
            return submission.getTask() != null && canManageTask(submission.getTask(), user);
        }
        return false;
    }

    public void checkManageSubmission(Submission submission, User user) {
        if (!canManageSubmission(submission, user)) {
            throw new AccessDeniedException("You are not authorized to review or manage this submission");
        }
    }

    public boolean canAccessSubmission(Submission submission, User user) {
        if (user == null || submission == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        if (user.getRole() == Role.MANAGER) {
            return submission.getTask() != null && canManageTask(submission.getTask(), user);
        }
        if (user.getRole() == Role.EMPLOYEE) {
            return (submission.getSubmittedBy() != null && user.getId().equals(submission.getSubmittedBy().getId()))
                    || (submission.getTask() != null && submission.getTask().getAssignedTo() != null && user.getId().equals(submission.getTask().getAssignedTo().getId()));
        }
        return false;
    }

    public void checkAccessSubmission(Submission submission, User user) {
        if (!canAccessSubmission(submission, user)) {
            throw new AccessDeniedException("You are not authorized to access this submission");
        }
    }
}
