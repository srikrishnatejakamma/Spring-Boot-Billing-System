package com.acme.billing;

import com.acme.billing.account.*;
import com.acme.billing.core.Plan;
import com.acme.billing.dto.AccountCommands;
import com.acme.testing.OidcFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:authorization;MODE=MySQL;DB_CLOSE_DELAY=-1", "billing.seed=false", "billing.scheduler.enabled=false", "billing.security.enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles({"demo", "security-test"})
class GraphqlAuthorizationTest {
    static final OidcFixture oidc = new OidcFixture();
    @DynamicPropertySource static void identity(DynamicPropertyRegistry properties) {
        properties.add("security.jwt.issuer", () -> oidc.issuer); properties.add("security.jwt.jwk-set-uri", oidc::jwks);
    }
    @Autowired MockMvc mvc; @Autowired AccountService service; @Autowired ObjectMapper json;
    @AfterAll static void close() { oidc.close(); }
    @Test void restrictedFieldIsNullWithAStructuredForbiddenError() throws Exception {
        var account = service.create(new AccountCommands.Create("Private account", "private@account.example", Plan.STARTER), "private-" + UUID.randomUUID());
        mvc.perform(post("/graphql").header("Authorization", "Bearer " + oidc.token("billing.read", "billing-api")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("query", "{ account(id:\"" + account.id + "\") { company billingEmail } }"))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.account.billingEmail").isEmpty()).andExpect(jsonPath("errors[0].extensions.code").value("FORBIDDEN"));
    }
    @Test void authorizedFieldCanBeReadAndRestDoesNotLeakItToReadOnlyTokens() throws Exception {
        var account = service.create(new AccountCommands.Create("Allowed account", "allowed@account.example", Plan.STARTER), "allowed-" + UUID.randomUUID());
        mvc.perform(get("/api/accounts/" + account.id).header("Authorization", "Bearer " + oidc.token("billing.read", "billing-api")))
                .andExpect(status().isOk()).andExpect(jsonPath("billingEmail").isEmpty());
        mvc.perform(post("/graphql").header("Authorization", "Bearer " + oidc.token("billing.read billing.pii", "billing-api"))
                .contentType("application/json").content("{\"query\":\"{ accounts { billingEmail } }\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("errors").doesNotExist())
                .andExpect(jsonPath("data.accounts[0].billingEmail").value("allowed@account.example"));
    }
    @Test void invalidPageLimitHasAStableErrorCode() throws Exception {
        mvc.perform(post("/graphql").header("Authorization", "Bearer " + oidc.token("billing.read", "billing-api"))
                .contentType("application/json").content("{\"query\":\"{ accounts(limit:0) { id } }\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("errors[0].extensions.code").value("BAD_REQUEST"));
    }
}
