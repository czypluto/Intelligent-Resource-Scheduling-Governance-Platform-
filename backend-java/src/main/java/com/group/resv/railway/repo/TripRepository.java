package com.group.resv.railway.repo;

import com.group.resv.railway.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByTravelDateOrderByTravelDateAsc(LocalDate date);

    Optional<Trip> findByTrainIdAndTravelDate(Long trainId, LocalDate date);

    List<Trip> findByTrainIdOrderByTravelDateDesc(Long trainId);
}
