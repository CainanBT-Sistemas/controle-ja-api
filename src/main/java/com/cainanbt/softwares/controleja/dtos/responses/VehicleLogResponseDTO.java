package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.entities.VehicleLog;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class VehicleLogResponseDTO {
    private UUID id;
    private Long date;
    private BigDecimal odometerReading;
    private Double dashboardKml;
    private String vehicleName;

    public static VehicleLogResponseDTO toDTO(VehicleLog entity) {
        return VehicleLogResponseDTO.builder()
                .id(entity.getId())
                .date(entity.getDate())
                .odometerReading(entity.getOdometerReading())
                .dashboardKml(entity.getDashboardKml())
                .vehicleName(entity.getVehicle().getName())
                .build();
    }
}
