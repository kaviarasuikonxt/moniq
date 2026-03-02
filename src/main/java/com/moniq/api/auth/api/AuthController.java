package com.moniq.api.auth.api;

import com.moniq.api.auth.AuthService;
import com.moniq.api.auth.dto.AuthResponse;
import com.moniq.api.auth.dto.LoginRequest;
import com.moniq.api.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
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
}