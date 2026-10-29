package com.cainanbt.softwares.controleja.configs;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.config.account-action")
public class AccountActionProperties {
    private String pepper = "";
    private int expiryMinutes = 15;
    private int maxAttempts = 5;
    private int cooldownSeconds = 60;
    private int ipWindowMinutes = 15;
    private int ipMaxRequests = 10;
    private int reactivationDays = 60;
    private boolean emailEnabled;
    private String sender = "";

    @PostConstruct
    void validate() {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("ACCOUNT_ACTION_TOKEN_PEPPER is required");
        }
    }
}
