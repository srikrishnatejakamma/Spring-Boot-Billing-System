package com.acme.billing.web;
import graphql.*;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.*;
import org.springframework.graphql.execution.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@Component
public class GraphqlErrors extends DataFetcherExceptionResolverAdapter {
    private static final Logger log = LoggerFactory.getLogger(GraphqlErrors.class);
    @Override protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        String code = "INTERNAL_ERROR"; String message = "Request could not be completed"; ErrorType type = ErrorType.INTERNAL_ERROR;
        if (exception instanceof AccessDeniedException) { code = "FORBIDDEN"; message = "The requested field requires additional permission"; type = ErrorType.FORBIDDEN; }
        else if (exception instanceof ResponseStatusException status) {
            code = status.getStatusCode().value() == 404 ? "NOT_FOUND" : "BAD_REQUEST";
            message = status.getReason(); type = status.getStatusCode().value() == 404 ? ErrorType.NOT_FOUND : ErrorType.BAD_REQUEST;
        } else log.error("graphql_request_failed cause={}", exception.getClass().getSimpleName());
        return GraphqlErrorBuilder.newError(environment).message(message).errorType(type).extensions(Map.of("code", code)).build();
    }
}
