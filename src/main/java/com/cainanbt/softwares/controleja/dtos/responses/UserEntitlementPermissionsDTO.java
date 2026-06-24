package com.cainanbt.softwares.controleja.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserEntitlementPermissionsDTO {
    private Boolean vehicleModule;
    private Boolean expandedLimits;
    private Boolean noAds;
}
