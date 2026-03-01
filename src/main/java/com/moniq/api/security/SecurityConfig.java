package com.moniq.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // For MVP APIs, disable CSRF (we’ll re-enable properly when UI + JWT is ready)
            .csrf(csrf -> csrf.disable())

            // Allow health + ping without auth, protect everything else
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ping", "/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )

            // Disable default form login redirect
            .httpBasic(Customizer.withDefaults())
            .formLogin(form -> form.disable());

        return http.build();
    }
}