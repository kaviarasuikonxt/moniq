package com.moniq.api.oauth;

import com.moniq.api.auth.AuthProvider;
import com.moniq.api.auth.UserEntity;
import com.moniq.api.auth.UserRepository;
import com.moniq.api.security.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Set;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final String frontendCallback;

  public OAuth2SuccessHandler(
      UserRepository userRepository,
      JwtService jwtService,
      @Value("${app.auth.frontend-callback}") String frontendCallback
  ) {
    this.userRepository = userRepository;
    this.jwtService = jwtService;
    this.frontendCallback = frontendCallback;
  }

  @Override
  @Transactional
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication
  ) throws IOException, ServletException {

    OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

    String email = (String) oAuth2User.getAttributes().get("email");
    String sub = (String) oAuth2User.getAttributes().get("sub");

    if (email == null || sub == null) {
      response.sendError(500, "Missing Google user attributes");
      return;
    }

    UserEntity user = userRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, sub)
        .orElseGet(() -> userRepository.findByEmail(email.toLowerCase()).orElse(null));

    if (user == null) {
      user = new UserEntity();
      user.setEmail(email.toLowerCase());
      user.setProvider(AuthProvider.GOOGLE);
      user.setProviderUserId(sub);
      user.setEmailVerified(true);
      user.setRoles(Set.of("USER"));
      userRepository.save(user);
    } else {
      user.setProvider(AuthProvider.GOOGLE);
      user.setProviderUserId(sub);
      user.setEmailVerified(true);
      if (user.getRoles() == null || user.getRoles().isEmpty()) {
        user.setRoles(Set.of("USER"));
      }
      userRepository.save(user);
    }

    String jwt = jwtService.createToken(user.getId(), user.getEmail(), user.getRoles());

    String redirect = UriComponentsBuilder
        .fromUriString(frontendCallback)
        .queryParam("token", jwt)
        .build()
        .toUriString();

    response.sendRedirect(redirect);
  }
}