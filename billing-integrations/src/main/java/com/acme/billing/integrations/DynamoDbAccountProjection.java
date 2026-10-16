package com.acme.billing.integrations;

import com.acme.billing.dto.AccountChanged;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

/** Version-guarded, replay-safe account projection using AWS SDK for Java v2. */
public final class DynamoDbAccountProjection implements AccountDestination {
    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbAccountProjection(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override public void synchronize(AccountChanged event) {
        var account = event.account();
        var item = Map.ofEntries(
                Map.entry("accountId", s(account.id().toString())),
                Map.entry("eventId", s(event.eventId().toString())),
                Map.entry("version", n(account.version())),
                Map.entry("company", s(account.company())),
                Map.entry("billingEmail", s(account.billingEmail())),
                Map.entry("plan", s(account.plan().name())),
                Map.entry("status", s(account.status().name())),
                Map.entry("monthlyRate", s(account.monthlyRate())),
                Map.entry("currency", s(account.currency())),
                Map.entry("outstandingBalance", s(account.outstandingBalance() == null ? "0.00" : account.outstandingBalance())));
        var request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(#version) OR #version < :incomingVersion")
                .expressionAttributeNames(Map.of("#version", "version"))
                .expressionAttributeValues(Map.of(":incomingVersion", n(account.version())))
                .build();
        try {
            dynamoDb.putItem(request);
        } catch (ConditionalCheckFailedException staleOrDuplicate) {
            // The condition can fail only when this projection already has this or a newer version.
            // Retrying the event is therefore safe and must not regress the projection.
        }
    }

    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
}
