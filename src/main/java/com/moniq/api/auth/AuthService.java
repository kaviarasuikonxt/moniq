package com.moniq.api.auth;

import com.moniq.api.repository.UserRepository;
import com.moniq.api.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public String registerLocal(String email, String rawPassword) {
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already registered");
    }

    UserEntity user = new UserEntity();
    user.setEmail(email.toLowerCase());
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setProvider(AuthProvider.LOCAL);
    user.setEmailVerified(false);
    user.setRoles(Set.of("USER"));

    userRepository.save(user);
    return jwtService.createToken(user.getId(), user.getEmail(), user.getRoles());
  }

  public String loginLocal(String email, String rawPassword) {
    UserEntity user = userRepository.findByEmail(email.toLowerCase())
        .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

    if (user.getProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
      throw new IllegalArgumentException("Use social login for this account");
    }

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("Invalid credentials");
    }

    return jwtService.createToken(user.getId(), user.getEmail(), user.getRoles());
  }
}