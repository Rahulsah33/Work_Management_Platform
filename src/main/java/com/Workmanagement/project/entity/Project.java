package com.Workmanagement.project.entity;

import com.Workmanagement.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "projects",
        indexes = {
                @Index(name = "idx_projects_manager_id", columnList = "manager_id"),
                @Index(name = "idx_projects_status", columnList = "status"),
                @Index(name = "idx_projects_created_at", columnList = "createdAt")
        }
)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    private LocalDate startDate;

    private LocalDate endDate;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Column(nullable = false)
    private LocalDateTime createdAt;


    public Project(String name, String description,
                   LocalDate startDate, LocalDate endDate,
                   ProjectStatus status, User manager) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.manager = manager;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ProjectStatus.PLANNED;
        }
    }

}
