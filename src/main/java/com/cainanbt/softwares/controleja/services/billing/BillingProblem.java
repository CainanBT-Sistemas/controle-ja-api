package com.cainanbt.softwares.controleja.services.billing;

import org.springframework.http.HttpStatus;

public final class BillingProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BillingProblem(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static BillingProblem invalid() {
        return new BillingProblem(HttpStatus.BAD_REQUEST, "BILLING_PURCHASE_INVALID",
                "A compra não pôde ser validada para esta conta.");
    }

    public static BillingProblem temporary() {
        return new BillingProblem(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_TEMPORARY_UNAVAILABLE",
                "A verificação de compra está temporariamente indisponível. Tente novamente.");
    }

    public static BillingProblem disabled() {
        return new BillingProblem(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_DISABLED",
                "Compras pelo Google Play estão indisponíveis neste ambiente.");
    }
}
