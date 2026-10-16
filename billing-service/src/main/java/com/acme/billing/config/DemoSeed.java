package com.acme.billing.config;

import com.acme.billing.dto.*;

import com.acme.billing.account.*;
import com.acme.billing.core.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration @Profile("demo")
@ConditionalOnProperty(name = "billing.seed", havingValue = "true", matchIfMissing = true)
public class DemoSeed {
    @Bean CommandLineRunner seed(AccountRepository accounts, AccountService service) {
        return args -> {
            if (accounts.count() != 0) return;
            String[][] companies = {{"Acme Studio", "finance@acme.example", "ENTERPRISE"},
                    {"Orbit Technologies", "billing@orbit.example", "GROWTH"},
                    {"Northstar Labs", "accounts@northstar.example", "ENTERPRISE"},
                    {"Bloom & Co.", "hello@bloom.example", "STARTER"},
                    {"Forma Design", "billing@forma.example", "GROWTH"},
                    {"Linear Logistics", "finance@linear.example", "GROWTH"},
                    {"Sunday Coffee", "team@sunday.example", "STARTER"},
                    {"Arc Systems", "billing@arc.example", "ENTERPRISE"}};
            for (int i = 0; i < companies.length; i++) {
                var row = companies[i];
                var account = service.create(new AccountCommands.Create(row[0], row[1], Plan.valueOf(row[2])), "demo-seed-" + i);
                if (i == 5) service.update(account.id, new AccountCommands.Update(account.company, account.billingEmail,
                        account.plan, AccountStatus.SUSPENDED, account.version));
            }
        };
    }
}
