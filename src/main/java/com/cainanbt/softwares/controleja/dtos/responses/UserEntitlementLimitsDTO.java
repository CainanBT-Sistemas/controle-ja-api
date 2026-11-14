package com.cainanbt.softwares.controleja.dtos.responses;

import com.cainanbt.softwares.controleja.services.entitlements.PlanLimits;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserEntitlementLimitsDTO {
    private Integer accounts;
    private Integer creditCards;
    private Integer vehicles;
    private Integer categories;
    private Integer subcategoriesPerParent;

    public static UserEntitlementLimitsDTO from(PlanLimits limits) {
        return new UserEntitlementLimitsDTO(
                limits.accounts(),
                limits.creditCards(),
                limits.vehicles(),
                limits.categories(),
                limits.subcategoriesPerParent()
        );
    }
}
