package com.moniq.api.auth.api;

import com.moniq.api.auth.AuthService;
import com.moniq.api.auth.dto.AuthResponse;
import com.moniq.api.auth.dto.LoginRequest;
import com.moniq.api.auth.dto.RefreshRequest;
import com.moniq.api.auth.dto.RegisterRequest;
import com.moniq.api.auth.dto.TokenPairResponse;
import com.moniq.api.auth.refresh.RefreshTokenService;
import com.moniq.api.auth.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;

  public AuthController(AuthService authService, RefreshTokenService refreshTokenService) {
    this.authService = authService;
    this.refreshTokenService = refreshTokenService;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
    try {
      authService.register(req);
      return ResponseEntity.ok().body("REGISTERED");
    } catch (IllegalArgumentException ex) {
      if ("EMAIL_ALREADY_EXISTS".equals(ex.getMessage())) {
        return ResponseEntity.status(409).body("EMAIL_ALREADY_EXISTS");
      }
      return ResponseEntity.badRequest().body("BAD_REQUEST");
    }
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
    try {
      AuthResponse res = authService.login(req);
      return ResponseEntity.ok(res);
    } catch (IllegalArgumentException ex) {
      return switch (ex.getMessage()) {
        case "USE_SOCIAL_LOGIN" -> ResponseEntity.status(409).body("USE_SOCIAL_LOGIN");
        case "INVALID_CREDENTIALS" -> ResponseEntity.status(401).body("INVALID_CREDENTIALS");
        default -> ResponseEntity.badRequest().body("BAD_REQUEST");
      };
    }
  }

 // ===== Day 5 endpoints =====

    /** New: returns access + refresh */
    @PostMapping("/login/v2")
    public ResponseEntity<TokenPairResponse> loginV2(@RequestBody LoginRequest req, HttpServletRequest request) {
        return ResponseEntity.ok(authService.loginV2(req, request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@RequestBody RefreshRequest req, HttpServletRequest request) {
        RefreshTokenService.TokenPair pair = refreshTokenService.refresh(req.getRefreshToken(), request);
        return ResponseEntity.ok(new TokenPairResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds()));
    }

    

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest req) {
        refreshTokenService.logout(req.getRefreshToken());
        return ResponseEntity.ok().build();
    }


}