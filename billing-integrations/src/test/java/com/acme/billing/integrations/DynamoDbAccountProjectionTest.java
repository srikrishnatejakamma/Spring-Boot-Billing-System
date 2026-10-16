package com.acme.billing.integrations;

import com.acme.billing.core.AccountStatus;
import com.acme.billing.core.Plan;
import com.acme.billing.dto.AccountChanged;
import com.acme.billing.dto.AccountSnapshot;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DynamoDbAccountProjectionTest {
    @Test void writesFullSnapshotWithMonotonicVersionCondition() {
        var client = mock(DynamoDbClient.class);
        var projection = new DynamoDbAccountProjection(client, "account-projection");
        var accountId = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        projection.synchronize(event(eventId, accountId, 7));

        var request = org.mockito.ArgumentCaptor.forClass(PutItemRequest.class);
        verify(client).putItem(request.capture());
        assertThat(request.getValue().tableName()).isEqualTo("account-projection");
        assertThat(request.getValue().conditionExpression())
                .isEqualTo("attribute_not_exists(#version) OR #version < :incomingVersion");
        assertThat(request.getValue().item()).containsKeys("accountId", "eventId", "version", "company",
                "billingEmail", "plan", "status", "monthlyRate", "currency", "outstandingBalance");
        assertThat(request.getValue().item().get("accountId").s()).isEqualTo(accountId.toString());
        assertThat(request.getValue().item().get("eventId").s()).isEqualTo(eventId.toString());
        assertThat(request.getValue().item().get("version").n()).isEqualTo("7");
        assertThat(request.getValue().item().get("monthlyRate").s()).isEqualTo("149.00");
    }

    @Test void treatsDuplicateOrOutOfOrderVersionAsAlreadyApplied() {
        var client = mock(DynamoDbClient.class);
        doThrow(ConditionalCheckFailedException.builder().message("stale version").build())
                .when(client).putItem(any(PutItemRequest.class));

        new DynamoDbAccountProjection(client, "account-projection").synchronize(event(UUID.randomUUID(), UUID.randomUUID(), 2));

        verify(client).putItem(any(PutItemRequest.class));
    }

    private AccountChanged event(UUID eventId, UUID accountId, long version) {
        var snapshot = new AccountSnapshot(accountId, "Acme", "billing@acme.example", Plan.GROWTH,
                AccountStatus.ACTIVE, "149.00", "USD", version, "12.34");
        return new AccountChanged(eventId, "ACCOUNT_UPDATED", Instant.parse("2026-01-01T00:00:00Z"), snapshot);
    }
}
