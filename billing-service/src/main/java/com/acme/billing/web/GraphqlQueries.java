package com.acme.billing.web;

import com.netflix.graphql.dgs.*;
import java.util.List;

@DgsComponent
public class GraphqlQueries {
    private final QueryService queries;
    public GraphqlQueries(QueryService queries) { this.queries = queries; }
    @DgsQuery public List<Views.Account> accounts(@InputArgument Integer limit) { return queries.accounts(limit == null ? 100 : limit); }
    @DgsQuery public Views.Account account(@InputArgument String id) { return queries.account(id); }
    @DgsQuery public Views.Overview overview() { return queries.overview(); }
    @DgsQuery public List<Views.Event> events(@InputArgument String accountId) { return queries.events(accountId); }
}
