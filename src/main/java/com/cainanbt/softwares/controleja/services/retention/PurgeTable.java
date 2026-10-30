package com.cainanbt.softwares.controleja.services.retention;

public enum PurgeTable {
    INSTALLMENT_PLAN("installment_plan"),
    TRANSACTIONS("transactions"),
    INVOICES("invoicess"),
    GAS_STATIONS("gas_stations"),
    RECURRENCE_RULES("recurrence_rules"),
    CREDIT_CARDS("credit_cards"),
    VEHICLES("vehicles"),
    CATEGORIES("category"),
    ACCOUNTS("accounts");

    private final String tableName;

    PurgeTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
