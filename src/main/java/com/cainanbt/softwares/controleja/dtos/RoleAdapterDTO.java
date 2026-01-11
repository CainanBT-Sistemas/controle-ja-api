package com.cainanbt.softwares.controleja.dtos;

import org.springframework.security.core.GrantedAuthority;

public class RoleAdapterDTO implements GrantedAuthority {

    private String role;

    public RoleAdapterDTO(String role) {
        this.role = role;
    }

    @Override
    public String getAuthority() {
        return this.role;
    }
}
