package com.moniq.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/ping")
    public String ping() {
        return "The MoniQ API Build via CI/CD is up and running!";
    }
}