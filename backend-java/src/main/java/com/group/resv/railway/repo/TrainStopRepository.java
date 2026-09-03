package com.group.resv.railway.repo;

import com.group.resv.railway.domain.TrainStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainStopRepository extends JpaRepository<TrainStop, Long> {

    List<TrainStop> findByTrainIdOrderBySeqAsc(Long trainId);

    void deleteByTrainId(Long trainId);
}
