package com.acme.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.net.URI;
import java.util.*;

@Configuration @EnableMethodSecurity
public class PlatformSecurityConfiguration {
    @Bean public AccessPolicy accessPolicy(Environment environment) {
        boolean secured = environment.getProperty("billing.security.enabled", Boolean.class, true);
        if (!secured && !environment.acceptsProfiles(Profiles.of("demo")))
            throw new IllegalStateException("Anonymous access is allowed only in the explicit demo profile");
        return new AccessPolicy(secured);
    }

    @Bean public SecurityFilterChain platformSecurity(HttpSecurity http, Environment environment,
                                                      AccessPolicy access, ObjectMapper json) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(cors(environment)))
                .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                        + "font-src 'self' https://fonts.gstatic.com; connect-src 'self' " + issuerOrigin(environment)
                        + "; base-uri 'self'; frame-ancestors 'none'; object-src 'none'")));
        if (!access.secured()) return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();

        String issuer = environment.getRequiredProperty("security.jwt.issuer");
        String jwks = environment.getRequiredProperty("security.jwt.jwk-set-uri");
        String audience = environment.getProperty("security.jwt.audience", "billing-api");
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", value -> value != null && value.contains(audience))));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/api/config", "/actuator/health/**").permitAll()
                .requestMatchers("/api/worker/**", "/actuator/metrics/**").hasAuthority("SCOPE_ops.read")
                .requestMatchers(HttpMethod.GET, "/api/**", "/graphiql/**").hasAuthority("SCOPE_billing.read")
                .requestMatchers("/graphql").hasAuthority("SCOPE_billing.read")
                .requestMatchers("/api/events/*/retry").hasAuthority("SCOPE_events.retry")
                .requestMatchers("/api/demo/**").denyAll()
                .requestMatchers("/api/**").hasAuthority("SCOPE_billing.write")
                .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder))
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401); response.setHeader("WWW-Authenticate", "Bearer");
                            response.setContentType("application/problem+json");
                            json.writeValue(response.getOutputStream(), Map.of("status", 401, "code", "UNAUTHENTICATED", "detail", "A valid access token is required"));
                        }))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/problem+json");
                    json.writeValue(response.getOutputStream(), Map.of("status", 403, "code", "FORBIDDEN", "detail", "The access token lacks the required permission"));
                }));
        return http.build();
    }

    private CorsConfigurationSource cors(Environment environment) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(environment.getProperty("security.cors.origins", "http://localhost:5173,http://localhost:8080").split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-ID"));
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", configuration); return source;
    }

    private String issuerOrigin(Environment environment) {
        URI uri = URI.create(environment.getProperty("security.jwt.issuer", "http://localhost:8180/realms/billing"));
        if (uri.getHost() == null || (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())))
            throw new IllegalArgumentException("OIDC issuer must be an HTTP(S) URL");
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
