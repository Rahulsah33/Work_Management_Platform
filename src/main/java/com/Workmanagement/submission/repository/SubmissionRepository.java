package com.Workmanagement.submission.repository;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionRepository
        extends JpaRepository<Submission, Long> {

    List<Submission> findByTaskId(Long taskId);

    List<Submission> findBySubmittedById(Long employeeId);

    List<Submission> findByStatus(SubmissionStatus status);

    List<Submission> findByTaskIdOrderBySubmittedAtDesc(Long taskId);

    List<Submission> findByTaskIdOrderByVersionDesc(Long taskId);

    List<Submission> findByTaskIdOrderByVersionAsc(Long taskId);

    java.util.Optional<Submission> findFirstByTaskIdOrderByVersionDesc(Long taskId);
}