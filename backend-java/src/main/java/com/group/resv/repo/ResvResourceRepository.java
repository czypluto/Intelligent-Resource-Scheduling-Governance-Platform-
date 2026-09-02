package com.group.resv.repo;

import com.group.resv.domain.ResvResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResvResourceRepository extends JpaRepository<ResvResource, Long> {

    List<ResvResource> findByTypeOrderByIdAsc(String type);

    List<ResvResource> findByStatusOrderByIdAsc(String status);
}
