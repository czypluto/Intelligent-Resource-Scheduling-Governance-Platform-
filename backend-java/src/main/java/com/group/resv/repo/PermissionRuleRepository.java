package com.group.resv.repo;

import com.group.resv.domain.PermissionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRuleRepository extends JpaRepository<PermissionRule, Long> {

    Optional<PermissionRule> findByResourceType(String resourceType);
}
