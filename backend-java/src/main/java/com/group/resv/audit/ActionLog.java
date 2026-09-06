package com.group.resv.audit;

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

/** 操作审计（人工接管/追责依据），写路径成功与失败都落一条。 */
@Entity
@Table(name = "action_log")
@Getter
@Setter
@NoArgsConstructor
public class ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 32)
    private String module;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private boolean success = true;

    @Column(length = 500)
    private String err;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
