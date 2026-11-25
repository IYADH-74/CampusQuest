package com.campusquest.security;

import com.campusquest.common.web.RequestIdFilter;
import com.campusquest.config.SecuritySettings;
import com.campusquest.security.ratelimit.ApiRateLimitFilter;
import com.campusquest.security.ratelimit.RateLimiter;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Deny-by-default security. URL rules are a coarse outer ring. Every controller method also declares its
 * roles with {@code @PreAuthorize}, and every service re-checks object-level access.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_AUTH_ENDPOINTS = {
        "/api/v1/auth/register",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/verify-email/resend",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/password/forgot",
        "/api/v1/auth/password/reset",
    };

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    private static final String DOCS_CSP = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
            + "script-src 'self' 'unsafe-inline'; frame-ancestors 'none'";

    /** Swagger UI needs scripts and styles, so it gets its own chain with a looser CSP (dev only). */
    @Bean
    @Order(1)
    @ConditionalOnBooleanProperty("springdoc.api-docs.enabled")
    SecurityFilterChain apiDocsChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, JwtDecoder jwtDecoder, JwtToUserConverter jwtConverter,
            AuthenticationEntryPoint entryPoint, AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // Stateless bearer API: no session, no cookies for auth (refresh cookie has its own CSRF guard).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeys keys) {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey()).privateKey(keys.privateKey()).keyID(keys.keyId()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeys keys, SecuritySettings settings) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        String audience = settings.jwt().audience();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.jwt().issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Argon2id for new hashes; bcrypt kept so legacy hashes could be verified and upgraded on login.
        return new DelegatingPasswordEncoder("argon2", Map.of(
                "argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecuritySettings settings) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(settings.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Requested-With",
                RequestIdFilter.HEADER));
        cors.setExposedHeaders(List.of(RequestIdFilter.HEADER, HttpHeaders.RETRY_AFTER));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    AuthenticationEntryPoint problemAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (request, response, ex) -> resolver.resolveException(request, response, null, ex);
    }

    @Bean
    AccessDeniedHandler problemAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (request, response, ex) -> resolver.resolveException(request, response, null, ex);
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ApiRateLimitFilter> apiRateLimitFilter(RateLimiter rateLimiter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        FilterRegistrationBean<ApiRateLimitFilter> registration =
                new FilterRegistrationBean<>(new ApiRateLimitFilter(rateLimiter, resolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
