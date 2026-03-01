package com.moniq.api.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/ping")
    public String ping() {
        return "The MoniQ API Build via CI/CD is up and running!";
    }

    @GetMapping("/debug/env")
        public Map<String, String> env() {
         return Map.of(
        "JWT_SECRET", System.getenv("JWT_SECRET") != null ? "SET" : "NOT SET",
        "GOOGLE_CLIENT_ID", System.getenv("GOOGLE_CLIENT_ID") != null ? "SET" : "NOT SET",
        "GOOGLE_CLIENT_SECRET", System.getenv("GOOGLE_CLIENT_SECRET") != null ? "SET" : "NOT SET",
        "FRONTEND_URL", System.getenv("FRONTEND_URL")
    );
}
}