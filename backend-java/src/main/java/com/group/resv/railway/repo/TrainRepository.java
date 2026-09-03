package com.group.resv.railway.repo;

import com.group.resv.railway.domain.Train;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByCode(String code);
}
