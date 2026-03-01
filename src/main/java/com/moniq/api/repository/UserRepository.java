package com.moniq.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.moniq.api.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}