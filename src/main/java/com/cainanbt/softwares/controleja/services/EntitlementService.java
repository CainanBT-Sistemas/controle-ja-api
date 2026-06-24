package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.responses.UserEntitlementsDTO;
import com.cainanbt.softwares.controleja.entities.Users;

public interface EntitlementService {
    UserEntitlementsDTO buildForUser(Users user);
}
