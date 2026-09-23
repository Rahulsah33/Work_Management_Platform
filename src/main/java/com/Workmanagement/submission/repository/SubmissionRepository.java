package com.Workmanagement.submission.repository;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    // Retrieve submissions for a specific task
    List<Submission> findByTask(Task task);
    List<Submission> findByTaskId(Long taskId);

    // Retrieve submissions made by a specific user / employee
    List<Submission> findBySubmittedBy(User user);
    List<Submission> findBySubmittedById(Long userId);

    // Filter submissions by review status
    List<Submission> findByStatus(SubmissionStatus status);

    // Retrieve submissions for a task matching a specific status
    List<Submission> findByTaskIdAndStatus(Long taskId, SubmissionStatus status);

    // Get the most recent submission for a task
    Optional<Submission> findFirstByTaskIdOrderBySubmittedAtDesc(Long taskId);

    // Check if any submission exists for a task
    boolean existsByTaskId(Long taskId);
}
