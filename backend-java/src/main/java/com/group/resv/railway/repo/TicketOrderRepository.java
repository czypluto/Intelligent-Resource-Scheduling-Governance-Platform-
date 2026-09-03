package com.group.resv.railway.repo;

import com.group.resv.railway.domain.TicketOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    Optional<TicketOrder> findByRequestId(String requestId);

    List<TicketOrder> findByUserIdOrderByIdDesc(Long userId);
}
