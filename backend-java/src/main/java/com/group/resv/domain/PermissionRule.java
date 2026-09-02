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

/**
 * 确定性权限规则：某资源类型可由哪些职级/部门预约。
 * 字段存英文逗号分隔的取值，空串或 NULL 表示不限。
 * 结论由 Java 依据此表判定，不经过大模型。
 */
@Entity
@Table(name = "permission_rule")
@Getter
@Setter
@NoArgsConstructor
public class PermissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_type", nullable = false, unique = true, length = 64)
    private String resourceType;

    @Column(name = "required_positions", length = 255)
    private String requiredPositions;

    @Column(name = "allowed_departments", length = 255)
    private String allowedDepartments;

    @Column(length = 255)
    private String note;
}
