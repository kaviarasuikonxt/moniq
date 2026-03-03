package com.moniq.api.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MeController {

  @GetMapping("/api/me")
  public Map<String, Object> me(Authentication auth) {
    return Map.of(
        "email", auth.getName(),
        "authorities", auth.getAuthorities(),
        "principle", auth.getPrincipal()
        //add more user details if needed
    );
  }
}