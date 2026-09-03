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

import java.time.LocalTime;

/** 列车经停站：停站顺序 + 到发时刻（对具体某车次固定）。 */
@Entity
@Table(name = "train_stop")
@Getter
@Setter
@NoArgsConstructor
public class TrainStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "train_id", nullable = false)
    private Long trainId;

    /** 0,1,2… 停站顺序 */
    @Column(nullable = false)
    private Integer seq;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "arrive_time")
    private LocalTime arriveTime;

    @Column(name = "depart_time")
    private LocalTime departTime;
}
