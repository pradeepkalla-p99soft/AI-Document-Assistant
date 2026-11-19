package com.example.docassistant.repository;

import com.example.docassistant.domain.UserRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserRecord, UUID> {
    Optional<UserRecord> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}

