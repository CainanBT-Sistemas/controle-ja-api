package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementPermissionsDTO;
import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.services.ClosedTestAccessPolicy;
import com.cainanbt.softwares.controleja.services.EntitlementService;
import org.springframework.stereotype.Service;

@Service
public class EntitlementServiceImp implements EntitlementService {

    private final ClosedTestAccessPolicy closedTestAccessPolicy;

    public EntitlementServiceImp(ClosedTestAccessPolicy closedTestAccessPolicy) {
        this.closedTestAccessPolicy = closedTestAccessPolicy;
    }

    @Override
    public UserEntitlementsDTO buildForUser(Users user) {
        boolean tester = user != null
                && user.getEmail() != null
                && closedTestAccessPolicy.isAllowlisted(user.getEmail());

        if (tester) {
            return new UserEntitlementsDTO(
                    "TESTER",
                    true,
                    true,
                    false,
                    new UserEntitlementPermissionsDTO(true, true, true)
            );
        }

        return new UserEntitlementsDTO(
                "FREE",
                false,
                false,
                true,
                new UserEntitlementPermissionsDTO(false, false, false)
        );
    }
}
