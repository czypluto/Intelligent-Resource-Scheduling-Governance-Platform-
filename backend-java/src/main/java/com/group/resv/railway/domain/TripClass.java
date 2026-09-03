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

/** 车次席别：席别 + 全程票价 + 总席位。余票运行期在 Redis 中维护。 */
@Entity
@Table(name = "trip_class")
@Getter
@Setter
@NoArgsConstructor
public class TripClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "seat_class", nullable = false, length = 32)
    private String seatClass;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;
}
