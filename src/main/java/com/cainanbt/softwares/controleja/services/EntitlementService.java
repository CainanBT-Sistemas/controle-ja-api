package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.enums.EffectivePlan;

public interface EntitlementService {
    UserEntitlementsDTO buildForUser(Users user);

    EffectivePlan resolvePlan(Users user);
}
