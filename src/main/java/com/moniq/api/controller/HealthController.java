package com.moniq.api.controller;

import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestController
public class HealthController {

     private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    @GetMapping("/ping")
    public String ping() {
        log.info("Applicaiton MONIQ running successfully");
        return "The MoniQ API Build via CI/CD is up and running!";
    }

    @GetMapping(value = "/auth/callback", produces = MediaType.TEXT_PLAIN_VALUE)
     public String callback(@RequestParam("token") String token) {
    return "JWT token:\n\n" + token + "\n";
     }

      @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
    public String root() {
        return "Moniq API is running. Try /ping";
    }

}