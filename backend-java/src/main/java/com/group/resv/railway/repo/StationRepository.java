package com.group.resv.railway.repo;

import com.group.resv.railway.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByName(String name);

    Optional<Station> findByCode(String code);

    List<Station> findByNameContainingOrderByIdAsc(String kw);
}
