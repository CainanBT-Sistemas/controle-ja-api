package com.cainanbt.softwares.controleja.configs;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.config.data-retention")
public class DataRetentionProperties {
    private boolean enabled = false;
    private boolean dryRun = true;
    @NotBlank
    private String cron = "0 30 4 * * *";
    @NotBlank
    private String zone = "America/Sao_Paulo";
    @Min(60)
    private int days = 60;
    @Min(1)
    private int batchSize = 100;
    @Min(1)
    private int maxBatchesPerRun = 20;
}
