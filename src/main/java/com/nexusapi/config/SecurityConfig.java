package com.nexusapi.config;

import com.nexusapi.repository.UserRepository;
import com.nexusapi.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for NexusAPI.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li><strong>Stateless sessions</strong> — {@code STATELESS} policy; no server-side
 *       session state. Every request is authenticated via JWT.</li>
 *   <li><strong>CSRF disabled</strong> — safe for stateless JWT APIs since there is no
 *       session cookie that a CSRF attack could exploit.</li>
 *   <li><strong>Method-level security</strong> — {@code @EnableMethodSecurity} enables
 *       {@code @PreAuthorize} on service and controller methods for fine-grained access
 *       control beyond URL patterns.</li>
 *   <li><strong>BCrypt password encoding</strong> — strength factor 12 (default 10) for
 *       a good balance between security and login latency.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    // ---------------------------------------------------------------------------
    // Public endpoints — no authentication required
    // ---------------------------------------------------------------------------

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/auth/**",       // Login, register, refresh
        "/actuator/health",      // Health checks (for load balancer probes)
        "/swagger-ui/**",        // Swagger UI assets
        "/swagger-ui.html",
        "/api-docs/**",          // OpenAPI JSON spec
        "/ws/**"                 // WebSocket handshake endpoint
    };

    // ---------------------------------------------------------------------------
    // Security filter chain
    // ---------------------------------------------------------------------------

    /**
     * Configures the main HTTP security filter chain.
     *
     * <p>The JWT filter is inserted before Spring's default username/password
     * filter so that token-authenticated requests bypass form-login processing.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        AuthenticationProvider authenticationProvider
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // Allow OPTIONS preflight requests for CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    // ---------------------------------------------------------------------------
    // Authentication provider
    // ---------------------------------------------------------------------------

    /**
     * Wires the {@link UserDetailsService} and {@link PasswordEncoder} into
     * Spring Security's authentication pipeline.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Loads users from the database by email for Spring Security authentication.
     * This bean is consumed by {@link DaoAuthenticationProvider}.
     */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return email -> userRepository.findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + email
            ));
    }

    /**
     * BCrypt with strength 12.
     * Each increment of strength doubles the hashing time:
     * strength 12 ≈ 300ms per hash — fast enough for UX, slow enough for security.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    // ---------------------------------------------------------------------------
    // CORS configuration
    // ---------------------------------------------------------------------------

    /**
     * CORS policy: allows the Flutter web app and development origins.
     * In production, restrict {@code allowedOrigins} to your specific domain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*")); // Tighten in production
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
