package com.amrit.scim.config;

import com.amrit.scim.dto.ScimError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security configuration for the SCIM service.
 * <p>
 * Stateless bearer-token authentication: every request to {@code /scim/v2/**}
 * must carry {@code Authorization: Bearer <token>}. The token is validated
 * against the static value in {@code application.properties}.
 * <p>
 * For production use, swap the static token for JWT validation via
 * {@code spring-boot-starter-oauth2-resource-server} and a JWKS endpoint.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${scim.auth.token}")
    private String expectedToken;

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/swagger-ui.html", "/swagger-ui/**",
                    "/api-docs", "/api-docs/**",
                    "/v3/api-docs", "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // Construct the filter inline — not as a @Bean — to prevent Spring Boot from
            // auto-registering it as a standalone servlet filter in addition to the security chain.
            .addFilterBefore(buildBearerTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Returns the bearer token filter as a plain instance, not a Spring bean.
     * Declaring it as a @Bean of type OncePerRequestFilter causes Spring Boot to
     * register it both in the security chain and as a servlet filter, invoking it twice.
     */
    private OncePerRequestFilter buildBearerTokenFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();
                if (path.startsWith("/swagger-ui") ||
                    path.startsWith("/api-docs") ||
                    path.startsWith("/v3/api-docs")) {
                    chain.doFilter(request, response);
                    return;
                }

                String authHeader = request.getHeader("Authorization");

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.debug("Missing or malformed Authorization header for path={}", path);
                    writeUnauthorized(response, "Missing or malformed Authorization header.");
                    return;
                }

                String token = authHeader.substring(7);

                if (!expectedToken.equals(token)) {
                    log.debug("Invalid bearer token for path={}", path);
                    writeUnauthorized(response, "Invalid bearer token.");
                    return;
                }

                var auth = new UsernamePasswordAuthenticationToken("scim-client", null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
                chain.doFilter(request, response);
            }

            private void writeUnauthorized(HttpServletResponse response, String detail)
                    throws IOException {
                ScimError error = ScimError.builder().status("401").detail(detail).build();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(error));
            }
        };
    }
}
