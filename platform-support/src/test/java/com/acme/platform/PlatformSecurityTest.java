package com.acme.platform;

import com.acme.testing.OidcFixture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PlatformSecurityTest.Application.class, properties = {"management.tracing.enabled=false", "billing.security.enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles("security-test")
class PlatformSecurityTest {
    static final OidcFixture oidc = new OidcFixture();
    @DynamicPropertySource static void identity(DynamicPropertyRegistry properties) {
        properties.add("security.jwt.issuer", () -> oidc.issuer); properties.add("security.jwt.jwk-set-uri", oidc::jwks);
        properties.add("security.jwt.audience", () -> "billing-api");
    }
    @Autowired MockMvc mvc;
    @AfterAll static void close() { oidc.close(); }
    @Test void anonymousRequestsAreRejected() throws Exception { mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized()); }
    @Test void validSignedTokenAndScopeAreAccepted() throws Exception {
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + oidc.token("billing.read", "billing-api")))
                .andExpect(status().isOk()).andExpect(header().exists("Content-Security-Policy"));
    }
    @Test void readTokenCannotWrite() throws Exception {
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + oidc.token("billing.read", "billing-api"))).andExpect(status().isForbidden());
    }
    @Test void issuerAudienceExpiryAndSignatureAreValidated() throws Exception {
        String[] tokens = {
                oidc.token("billing.read", "https://other.example", "billing-api", Instant.now().plusSeconds(300), false),
                oidc.token("billing.read", "other-audience"),
                oidc.token("billing.read", oidc.issuer, "billing-api", Instant.now().minusSeconds(120), false),
                oidc.token("billing.read", oidc.issuer, "billing-api", Instant.now().plusSeconds(300), true), "not-a-jwt" };
        for (String token : tokens) mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
    @Test void correlationHeaderCannotInjectLogLines() throws Exception {
        mvc.perform(get("/api/config").header("X-Request-ID", "an-untrusted-value-that-is-deliberately-longer-than-the-sixty-four-character-limit"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", org.hamcrest.Matchers.matchesPattern("[a-f0-9-]{36}")));
    }
    @SpringBootConfiguration @EnableAutoConfiguration
    @Import({PlatformSecurityConfiguration.class, PlatformObservabilityConfiguration.class, Endpoints.class})
    static class Application {}
    @RestController static class Endpoints {
        @GetMapping({"/api/accounts", "/api/config"}) Map<String, String> get() { return Map.of("result", "ok"); }
        @PostMapping("/api/accounts") Map<String, String> post() { return Map.of("result", "created"); }
    }
}
