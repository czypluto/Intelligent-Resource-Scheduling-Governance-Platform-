package com.group.resv.repo;

import com.group.resv.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByUserIdOrderByIdAsc(Long userId);

    Optional<Contact> findByIdAndUserId(Long id, Long userId);
}
