package com.cainanbt.softwares.controleja.enums;

public enum RoleEnum {
    ROLE_USER("USER"),
    ROLE_ADMIN("ADMIN");

    private final String value;

    RoleEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
