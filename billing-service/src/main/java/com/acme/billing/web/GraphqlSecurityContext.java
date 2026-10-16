package com.acme.billing.web;
import org.springframework.context.annotation.*;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class GraphqlSecurityContext {
    @Bean WebGraphQlInterceptor authenticationContext() {
        return (request, chain) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) request.configureExecutionInput((input, builder) -> builder
                    .graphQLContext(context -> context.put("authentication", authentication)).build());
            return chain.next(request);
        };
    }
}
