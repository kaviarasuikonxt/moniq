package com.moniq.api.repository;

import com.moniq.api.auth.AuthProvider;
import com.moniq.api.auth.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByEmail(String email);
  Optional<UserEntity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
  boolean existsByEmail(String email);
}