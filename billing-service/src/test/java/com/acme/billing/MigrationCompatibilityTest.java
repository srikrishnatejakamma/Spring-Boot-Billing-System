package com.acme.billing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationCompatibilityTest {
    @Test void additiveMigrationPreservesExistingRowsAndOldInsertStatements() throws Exception {
        String url = "jdbc:h2:mem:upgrade" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("1").load().migrate();
        String insert = "INSERT INTO billing_accounts(id,company,billing_email,plan,status,monthly_rate,currency,version,created_at,updated_at) VALUES ('%s','Legacy','legacy@example.test','STARTER','ACTIVE',49.00,'USD',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.executeUpdate(insert.formatted("legacy-before-upgrade"));
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
            statement.executeUpdate(insert.formatted("legacy-after-upgrade"));
            try (var result = statement.executeQuery("SELECT company, outstanding_balance FROM billing_accounts ORDER BY id")) {
                int count = 0; while (result.next()) { count++; assertThat(result.getString(1)).isEqualTo("Legacy"); assertThat(result.getBigDecimal(2)).isEqualByComparingTo("0.00"); }
                assertThat(count).isEqualTo(2);
            }
            Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().validate();
        }
    }
}
