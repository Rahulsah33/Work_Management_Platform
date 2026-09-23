package com.Workmanagement.task.repository;

import com.Workmanagement.task.entity.TaskRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRequirementRepository extends JpaRepository<TaskRequirement, Long> {
    List<TaskRequirement> findByTaskId(Long taskId);
}
