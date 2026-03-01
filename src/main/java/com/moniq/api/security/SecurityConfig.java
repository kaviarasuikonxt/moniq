package com.moniq.api.security;

import com.moniq.api.oauth.OAuth2SuccessHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  // ---- 1) Default chain (NO OAuth2) ----
  @Bean
  @ConditionalOnProperty(name = "app.auth.google.enabled", havingValue = "false", matchIfMissing = true)
  public SecurityFilterChain filterChainNoOAuth(HttpSecurity http, JwtService jwtService) throws Exception {

    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/ping").permitAll()
             .requestMatchers("/debug/**").permitAll()  
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated()
        )
        .httpBasic(Customizer.withDefaults());

    http.addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  // ---- 2) OAuth2 chain (Google enabled) ----
  @Bean
  @ConditionalOnProperty(name = "app.auth.google.enabled", havingValue = "true")
  public SecurityFilterChain filterChainWithOAuth(
      HttpSecurity http,
      JwtService jwtService,
      OAuth2SuccessHandler oAuth2SuccessHandler
  ) throws Exception {

    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/ping").permitAll()
            .requestMatchers("/auth/**").permitAll()
            .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth -> oauth.successHandler(oAuth2SuccessHandler))
        .httpBasic(Customizer.withDefaults());

    http.addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}