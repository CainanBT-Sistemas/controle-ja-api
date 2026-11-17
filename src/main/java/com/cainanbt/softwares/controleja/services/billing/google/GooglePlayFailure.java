package com.cainanbt.softwares.controleja.services.billing.google;

public final class GooglePlayFailure extends RuntimeException {
    private final boolean invalidPurchase;

    public GooglePlayFailure(boolean invalidPurchase) {
        super(invalidPurchase ? "Compra inválida na Google Play." : "Google Play indisponível.");
        this.invalidPurchase = invalidPurchase;
    }

    public boolean isInvalidPurchase() {
        return invalidPurchase;
    }
}
