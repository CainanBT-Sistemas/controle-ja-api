package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.configs.AccountActionProperties;
import com.cainanbt.softwares.controleja.enums.AccountActionPurpose;
import com.cainanbt.softwares.controleja.services.AccountActionEmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpAccountActionEmailSender implements AccountActionEmailSender {
    private final JavaMailSender mailSender;
    private final AccountActionProperties properties;

    @Override
    @Async("accountActionExecutor")
    public void sendCode(String recipient, String code, AccountActionPurpose purpose, int expiryMinutes) {
        try {
            requireEnabled();
            String action = purpose == AccountActionPurpose.PASSWORD_RESET
                    ? "redefinir sua senha"
                    : "reativar sua conta";
            SimpleMailMessage message = baseMessage(recipient);
            message.setSubject("Controle Já - código de acesso");
            message.setText("Use o código " + code + " para " + action +
                    ". Ele expira em " + expiryMinutes + " minutos. " +
                    "Se você não solicitou esta ação, ignore esta mensagem.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.error(
                    "account_action_email_delivery_failed purpose={} exception={}",
                    purpose,
                    exception.getClass().getSimpleName()
            );
        }
    }

    @Override
    @Async("accountActionExecutor")
    public void sendGoogleAccessGuidance(String recipient) {
        try {
            requireEnabled();
            SimpleMailMessage message = baseMessage(recipient);
            message.setSubject("Controle Já - acesso com Google");
            message.setText("Esta conta usa acesso pelo Google. Volte ao aplicativo e escolha Entrar com Google. " +
                    "Nenhuma senha foi criada ou alterada.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.error(
                    "account_action_email_delivery_failed purpose=GOOGLE_ACCESS_GUIDANCE exception={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private SimpleMailMessage baseMessage(String recipient) {
        if (properties.getSender() == null || properties.getSender().isBlank()) {
            throw new IllegalStateException("Account action email sender is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getSender());
        message.setTo(recipient);
        return message;
    }

    private void requireEnabled() {
        if (!properties.isEmailEnabled()) {
            throw new IllegalStateException("Account action email delivery is disabled");
        }
    }
}
