package com.Workmanagement.user.entity;

import jakarta.persistence.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false )
    private String name;

    @Column(nullable = false , unique = true)
    private String email;

    @Column(nullable = false )
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false )
    private Role role;





}
