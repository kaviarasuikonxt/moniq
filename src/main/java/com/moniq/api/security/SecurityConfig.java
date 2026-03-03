package com.moniq.api.security;

import com.moniq.api.oauth.OAuth2SuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;

@Configuration
public class SecurityConfig {

  @Value("${spring.security.oauth2.client.registration.google.client-id:}")
  private String googleClientId;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

 private final JwtAuthFilter jwtAuthFilter;
    public SecurityConfig(JwtAuthFilter jwtAuthFilter, OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtService jwtService,
      OAuth2SuccessHandler oAuth2SuccessHandler
  ) throws Exception {

    
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
           .requestMatchers(
                            "/auth/register",
                            "/auth/login",
                            "/auth/login/v2",
                            "/auth/refresh",
                            "/auth/logout",
                            "/oauth2/**",
                            "/login/**"
                    ).permitAll()
                    .anyRequest().authenticated()
        )
        .oauth2Login(oauth -> oauth.successHandler(oAuth2SuccessHandler))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(Customizer.withDefaults());

    // JWT for API calls
    http.addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

    // Enable Google OAuth2 ONLY if google client-id is present
    if (googleClientId != null && !googleClientId.isBlank()) {
      http.oauth2Login(oauth -> oauth.successHandler(oAuth2SuccessHandler));
    }

    // IMPORTANT: do NOT enable httpBasic() -> avoids browser popup
    return http.build();
  }
}