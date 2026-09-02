package com.group.resv.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt 密文 */
    @Column(nullable = false, length = 128)
    private String password;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 64)
    private String department;

    /** 职级，如 高管 / 部门正职 / 高级工程师 / 实习生 */
    @Column(length = 64)
    private String position;

    @Column(length = 32)
    private String role;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
