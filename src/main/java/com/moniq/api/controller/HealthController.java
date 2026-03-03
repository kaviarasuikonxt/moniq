package com.moniq.api.controller;

import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/ping")
    public String ping() {
        return "The MoniQ API Build via CI/CD is up and running!";
    }

    @GetMapping(value = "/auth/callback", produces = MediaType.TEXT_PLAIN_VALUE)
     public String callback(@RequestParam("token") String token) {
    return "JWT token:\n\n" + token + "\n";
     }

}