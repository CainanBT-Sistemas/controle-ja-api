package com.cainanbt.softwares.controleja.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserEntitlementsDTO {
    private String plan;
    private Boolean tester;
    private Boolean plusEquivalent;
    private Boolean adsEnabled;
    private UserEntitlementPermissionsDTO permissions;
}
