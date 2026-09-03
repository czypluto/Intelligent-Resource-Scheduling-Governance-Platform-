package com.group.resv.railway.repo;

import com.group.resv.railway.domain.TripClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripClassRepository extends JpaRepository<TripClass, Long> {

    List<TripClass> findByTripIdOrderByIdAsc(Long tripId);

    Optional<TripClass> findByTripIdAndSeatClass(Long tripId, String seatClass);
}
