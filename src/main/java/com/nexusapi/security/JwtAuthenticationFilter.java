package com.nexusapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — runs once per request before Spring Security's
 * standard filter chain.
 *
 * <p>Processing flow:
 * <ol>
 *   <li>Extract the {@code Authorization: Bearer <token>} header</li>
 *   <li>If absent or malformed, pass through to the next filter unchanged</li>
 *   <li>Parse and validate the JWT using {@link JwtService}</li>
 *   <li>Load the {@link UserDetails} from the database</li>
 *   <li>If valid, populate the {@link SecurityContextHolder} so downstream
 *       code can access the authenticated user via
 *       {@code SecurityContextHolder.getContext().getAuthentication()}</li>
 * </ol>
 *
 * <p>Stateless design: no session is created or used. Each request must
 * carry its own JWT. This is the standard approach for REST APIs consumed
 * by mobile apps and SPAs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // Pass through if no Bearer token is present — the request will be
        // handled as anonymous, and Spring Security will reject it if the
        // endpoint requires authentication.
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            // Only authenticate if not already authenticated in this request context.
            // This prevents redundant DB lookups on requests with multiple security layers.
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    // Create an authenticated token with full authority list.
                    // Setting details allows Spring Security audit logging to
                    // record the originating IP address.
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,  // Credentials are null — the JWT is the credential
                            userDetails.getAuthorities()
                        );
                    authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {}", userEmail);
                }
            }
        } catch (Exception e) {
            // Log but do not rethrow — an invalid JWT should result in a 401,
            // not a 500. The request continues as unauthenticated.
            log.warn("JWT authentication failed for request {}: {}", request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
