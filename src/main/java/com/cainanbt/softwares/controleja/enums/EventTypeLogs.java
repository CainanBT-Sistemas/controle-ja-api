package com.cainanbt.softwares.controleja.enums;

public enum EventTypeLogs {
    EVENT_INFO("INFO"),
    EVENT_WARN("WARN"),
    EVENT_ERRO("ERRO");

    private final String value;

    EventTypeLogs(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
