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

@Configuration
public class SecurityConfig {

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * ✅ Ensure JwtAuthFilter is a Spring bean
     * (so SecurityConfig can always inject/use it safely)
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
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
                            "/login/**",
                            "/actuator/health",
                            "/actuator/info"
                    ).permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
            // ✅ Add filter only ONCE
           

              // ✅ Only configure oauth2Login when Google is actually configured
        if (googleClientId != null && !googleClientId.isBlank()) {
            http.oauth2Login(oauth -> oauth.successHandler(oAuth2SuccessHandler));
        }
        // IMPORTANT: do NOT enable httpBasic() -> avoids browser popup
        // If you truly need it for debugging, add it back explicitly.
        return http.build();
    }
}