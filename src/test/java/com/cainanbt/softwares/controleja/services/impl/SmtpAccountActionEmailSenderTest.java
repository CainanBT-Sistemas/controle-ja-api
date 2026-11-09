package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpAccountActionEmailSenderTest {

    @Test
    void sendsEmailVerificationCodeUsingPublicSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AccountActionProperties properties = new AccountActionProperties();
        properties.setEmailEnabled(true);
        properties.setSender("support@controleja.test");
        SmtpAccountActionEmailSender sender = new SmtpAccountActionEmailSender(mailSender, properties);

        sender.sendCode(
                "recipient@test.com",
                "123456",
                AccountActionPurpose.EMAIL_VERIFICATION,
                15
        );

        var messageCaptor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("support@controleja.test", message.getFrom());
        assertEquals("recipient@test.com", message.getTo()[0]);
        assertTrue(message.getText().contains("confirmar seu e-mail"));
        assertTrue(message.getText().contains("123456"));
        assertTrue(message.getText().contains("15 minutos"));
    }
}
