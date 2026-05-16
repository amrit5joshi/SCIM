package com.amrit.scim.config;

import com.amrit.scim.dto.ScimError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
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

    /** The static bearer token read from application.properties. */
    @Value("${scim.auth.token}")
    private String expectedToken;

    private final ObjectMapper objectMapper;

    /**
     * Defines the filter chain:
     * <ol>
     *   <li>Disable CSRF — SCIM is a stateless REST API; CSRF only matters for browser forms.</li>
     *   <li>Disable sessions — each request is authenticated independently.</li>
     *   <li>Allow Swagger UI and OpenAPI spec paths without a token (dev convenience).</li>
     *   <li>Require authentication on all other routes.</li>
     *   <li>Insert our custom bearer-token filter before the default username/password filter.</li>
     * </ol>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Swagger UI and OpenAPI spec are public — no token needed for dev
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs",
                    "/api-docs/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**"
                ).permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(bearerTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Creates a filter that reads the {@code Authorization} header, extracts
     * the bearer token, and compares it to {@code scim.auth.token}.
     * <p>
     * On mismatch or missing header it writes a SCIM-formatted 401 response
     * directly — bypassing the controller layer — so the client always gets
     * a properly shaped error even for auth failures.
     */
    @Bean
    public OncePerRequestFilter bearerTokenFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, IOException {

                // Let Swagger paths through without checking the token
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

                String token = authHeader.substring(7); // strip "Bearer "

                if (!expectedToken.equals(token)) {
                    log.debug("Invalid bearer token for path={}", path);
                    writeUnauthorized(response, "Invalid bearer token.");
                    return;
                }

                // Token is valid — mark request as authenticated and continue
                var auth = new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken("scim-client", null, List.of());
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().setAuthentication(auth);

                chain.doFilter(request, response);
            }

            private void writeUnauthorized(HttpServletResponse response, String detail)
                    throws IOException {
                ScimError error = ScimError.builder()
                        .status("401")
                        .detail(detail)
                        .build();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(error));
            }
        };
    }
}
