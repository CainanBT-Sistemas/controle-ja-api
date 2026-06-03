package com.cainanbt.softwares.controleja.dtos.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataDTO {
    private String label;
    private BigDecimal value;
    private String color;
}
