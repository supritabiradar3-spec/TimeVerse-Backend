package com.timeverse.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.timeverse.backend.security.JwtAuthenticationFilter;


@Configuration
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:https://timeverse-xpo135qhj-time-verse1.vercel.app,http://localhost:5500,http://127.0.0.1:5500,http://localhost:5501,http://127.0.0.1:5501,http://localhost:3000,http://127.0.0.1:3000,http://localhost:8080}")
    private String allowedOriginsConfig;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ================= PUBLIC AUTH APIs =================
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/verify-otp",
                                "/api/auth/verify-login-otp",
                                "/api/auth/reset-password",
                                "/api/auth/verify-registration-otp",
                                "/api/auth/resend-registration-otp",
                                "/api/ai/chat"
                        ).permitAll()

                        // ================= CART APIs =================
                        // CartService requires the authenticated user's JWT, so do not expose these
                        // endpoints as public APIs. This also makes expired sessions fail cleanly.
                        .requestMatchers("/api/cart/**")
                        .authenticated()

                        // ================= CATEGORY APIs =================
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/categories",
                                "/api/categories/**"
                        ).permitAll()

                        // ================= PRODUCT APIs =================
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products",
                                "/api/products/**"
                        ).permitAll()

                        // ================= PUBLIC REVIEWS & COUPONS =================
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reviews/product/**",
                                "/api/coupons/**"
                        ).permitAll()

                        // ================= SWAGGER =================
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()

                        // ================= STATIC IMAGE & ASSET RESOURCES =================
                        .requestMatchers(
                                HttpMethod.GET,
                                "/images/**",
                                "/static/**",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico"
                        ).permitAll()

                        // ================= ADMIN MANAGEMENT & STORE APIs =================
                        .requestMatchers(
                                "/api/admin/**",
                                "/api/admin-mgmt/**"
                        ).hasRole("ADMIN")

                        // ================= ADMIN PRODUCT APIs =================
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/products/**"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/products/**"
                        ).hasRole("ADMIN")

                        // ================= ADMIN ORDER APIs =================
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/orders/**"
                        ).hasRole("ADMIN")

                        // ================= PAYMENT APIs =================
                        // Create payment - logged in user
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payments/create/**"
                        ).authenticated()

                        // View payment
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/payments/order/**"
                        ).authenticated()

                        // Update payment status - Admin / Customer
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/payments/**"
                        ).hasAnyRole("ADMIN", "CUSTOMER")

                        // ================= LOGOUT =================
                        .requestMatchers(
                                "/api/auth/logout"
                        ).authenticated()

                        // ================= OTHER APIs =================
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();

        java.util.List<String> origins = new java.util.ArrayList<>();
        if (allowedOriginsConfig != null && !allowedOriginsConfig.trim().isEmpty()) {
            for (String origin : allowedOriginsConfig.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty() && !origins.contains(trimmed)) {
                    origins.add(trimmed);
                }
            }
        }

        // Explicitly guarantee essential origins are included
        java.util.List<String> defaults = java.util.List.of(
                "https://timeverse-xpo135qhj-time-verse1.vercel.app",
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:5501",
                "http://127.0.0.1:5501",
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://localhost:8080"
        );
        for (String def : defaults) {
            if (!origins.contains(def)) {
                origins.add(def);
            }
        }

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedOriginPatterns(java.util.List.of(
                "https://*.vercel.app",
                "https://*-time-verse1.vercel.app",
                "https://*.onrender.com",
                "http://localhost:[*]",
                "http://127.0.0.1:[*]"
        ));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setExposedHeaders(java.util.List.of("Authorization", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}