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

    /** 年龄（购票系统保留字段，为后续 Agent 身份识别提供基础） */
    private Integer age;

    @Column(length = 8)
    private String gender;

    @Column(name = "id_type", length = 16)
    private String idType;

    @Column(name = "id_no", length = 32)
    private String idNo;

    @Column(length = 32)
    private String role;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
