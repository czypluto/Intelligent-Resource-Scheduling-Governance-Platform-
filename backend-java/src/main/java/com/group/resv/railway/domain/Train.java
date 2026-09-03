package com.group.resv.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 列车（车底）：G101 等。停站序列固定于列车，运行日期另建 trip。 */
@Entity
@Table(name = "train")
@Getter
@Setter
@NoArgsConstructor
public class Train {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(length = 64)
    private String name;

    /** 车型 G/D/K/Z/T 等 */
    @Column(length = 16)
    private String kind;
}
