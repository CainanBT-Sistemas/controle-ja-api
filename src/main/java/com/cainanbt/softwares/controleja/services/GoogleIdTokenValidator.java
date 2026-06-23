package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.dtos.GoogleIdentityDTO;

public interface GoogleIdTokenValidator {

    /**
     * Valida assinatura, issuer, audience e expiracao do idToken Google.
     */
    GoogleIdentityDTO validate(String idToken);
}
