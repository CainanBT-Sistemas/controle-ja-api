package com.cainanbt.softwares.controleja.services;

import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;

public interface AccountActionEmailSender {
    void sendCode(String recipient, String code, AccountActionPurpose purpose, int expiryMinutes);

    void sendGoogleAccessGuidance(String recipient);
}
