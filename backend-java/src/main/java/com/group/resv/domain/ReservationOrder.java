package com.group.resv.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_resource", columnNames = {"user_id", "resource_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ReservationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 幂等键：前端/Agent 生成的 request_id */
    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "seat_no", length = 32)
    private String seatNo;

    /** SUCCESS / DUPLICATE */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
