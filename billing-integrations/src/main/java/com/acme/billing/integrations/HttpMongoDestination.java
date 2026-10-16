package com.acme.billing.integrations;

import com.acme.billing.core.AccountChanged;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.client.RestClient;

/** External endpoint must honor Idempotency-Key and reject lower aggregate versions. */
public class HttpMongoDestination implements AccountDestination {
    private final RestClient platform;
    private final MongoTemplate mongo;

    public HttpMongoDestination(RestClient platform, MongoTemplate mongo) {
        this.platform = platform;
        this.mongo = mongo;
    }

    @Override public void synchronize(AccountChanged event) {
        platform.put().uri("/accounts/{id}", event.account().id())
                .header("Idempotency-Key", event.eventId().toString())
                .body(event.account()).retrieve().toBodilessEntity();

        // A higher projection version can never be overwritten by a late or replayed event.
        String id = event.account().id().toString();
        Query identity = Query.query(Criteria.where("_id").is(id));
        mongo.upsert(identity, new Update().setOnInsert("version", -1L), "account_views");
        Query newer = Query.query(Criteria.where("_id").is(id).and("version").lt(event.account().version()));
        mongo.updateFirst(newer, new Update()
                .set("company", event.account().company())
                .set("billingEmail", event.account().billingEmail())
                .set("plan", event.account().plan().name())
                .set("status", event.account().status().name())
                .set("monthlyRate", event.account().monthlyRate())
                .set("currency", event.account().currency())
                .set("version", event.account().version())
                .set("lastEventId", event.eventId().toString()), Document.class, "account_views");
    }
}
