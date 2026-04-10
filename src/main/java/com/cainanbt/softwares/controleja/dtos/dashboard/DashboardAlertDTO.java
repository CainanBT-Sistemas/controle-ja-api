package com.cainanbt.softwares.controleja.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardAlertDTO {
    private UUID id;
    private String description;
    private BigDecimal amount;
    private Long dueDate;
    private String icon;
    private String color;
    private String type;
}
