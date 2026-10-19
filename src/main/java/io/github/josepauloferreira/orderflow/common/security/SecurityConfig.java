package io.github.josepauloferreira.orderflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products/{id}")
                    .permitAll()
                    .requestMatchers("/api/v1/products/{productId}/stock/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/products/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders")
                    .hasRole("CUSTOMER")
                    .requestMatchers("/api/v1/orders/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, failure) ->
                            writeProblem(
                                response, mapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                "Authentication is required"))
                    .accessDeniedHandler(
                        (request, response, failure) ->
                            writeProblem(
                                response, mapper, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                "Access denied")))
        .oauth2ResourceServer(
            resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::rolesFromRealm);
    return converter;
  }

  private static Collection<GrantedAuthority> rolesFromRealm(Jwt jwt) {
    var authorities = new ArrayList<GrantedAuthority>();
    Object realm = jwt.getClaim("realm_access");
    if (realm instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
      for (Object role : roles) {
        if ("ADMIN".equals(role) || "CUSTOMER".equals(role)) {
          authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
      }
    }
    return authorities;
  }

  private static void writeProblem(
      jakarta.servlet.http.HttpServletResponse response,
      ObjectMapper mapper,
      HttpStatus status,
      String code,
      String detail)
      throws IOException {
    var body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setProperty("code", code);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), body);
  }
}
