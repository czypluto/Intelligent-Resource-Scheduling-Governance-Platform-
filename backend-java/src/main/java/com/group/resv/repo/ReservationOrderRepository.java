package com.group.resv.repo;

import com.group.resv.domain.ReservationOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationOrderRepository extends JpaRepository<ReservationOrder, Long> {

    Optional<ReservationOrder> findByRequestId(String requestId);

    Optional<ReservationOrder> findByUserIdAndResourceId(Long userId, Long resourceId);
}
