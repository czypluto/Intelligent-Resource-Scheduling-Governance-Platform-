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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 可预约资源。type 为资源类型编码（如 EXEC_SHUTTLE/MEETING_A），
 * 同一类型可按日期（班车按天）拆成多条资源记录。
 */
@Entity
@Table(name = "resource")
@Getter
@Setter
@NoArgsConstructor
public class ResvResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 资源类型编码，对应权限规则表 resource_type */
    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    /** 预约日期，班车类按天生成记录 */
    @Column(name = "reserve_date")
    private LocalDate reserveDate;

    @Column(length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
