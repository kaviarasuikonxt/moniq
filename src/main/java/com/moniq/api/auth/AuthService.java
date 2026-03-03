package com.moniq.api.auth;

import com.moniq.api.auth.dto.AuthResponse;
import com.moniq.api.auth.dto.LoginRequest;
import com.moniq.api.auth.dto.RegisterRequest;
import com.moniq.api.auth.dto.TokenPairResponse;
import com.moniq.api.auth.refresh.RefreshTokenService;
import com.moniq.api.repository.UserRepository;
import com.moniq.api.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,RefreshTokenService refreshTokenService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
  }

  @Transactional
  public void register(RegisterRequest req) {
    String email = req.email().trim().toLowerCase();

    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("EMAIL_ALREADY_EXISTS");
    }

    UserEntity user = new UserEntity();
    user.setEmail(email);
    user.setProvider(AuthProvider.LOCAL);
    user.setProviderUserId(null);
    user.setEmailVerified(false); // later we add verification
    user.setPasswordHash(passwordEncoder.encode(req.password()));
    user.setRoles(Set.of("USER"));

    userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest req) {
    String email = req.email().trim().toLowerCase();

    UserEntity user = userRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("INVALID_CREDENTIALS"));

    // If account is social-only
    if (user.getProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
      throw new IllegalArgumentException("USE_SOCIAL_LOGIN");
    }

    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
      throw new IllegalArgumentException("INVALID_CREDENTIALS");
    }

    String token = jwtService.createToken(user.getId(), user.getEmail(), user.getRoles());
    return new AuthResponse(token);
  }

/** Day 5: v2 login returns Access + Refresh (does NOT break Day 4 login) */
    public TokenPairResponse loginV2(LoginRequest req, HttpServletRequest request) {
        UserEntity user = userRepository.findByEmail(req.email().toLowerCase().trim())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (user.getProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new RuntimeException("Use Google login for this account");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        RefreshTokenService.TokenPair pair = refreshTokenService.issueNewSession(user, request);
        return new TokenPairResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds());
    }

}