package org.campuslab.notify.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.campuslab.notify.dto.EmailPayload;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {
    private final JavaMailSender mailSender;
    private final PushNotificationService pushNotificationService;
    private final SpringTemplateEngine templateEngine;

    public boolean send(EmailPayload payload) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(payload.getTo());
            helper.setSubject(payload.getSubject());
            Context context = new Context();
            context.setVariable("body", payload.getBody());
            helper.setText(templateEngine.process("email", context), true);
            mailSender.send(message);
            if (payload.getPushToken() != null && !payload.getPushToken().isBlank()) {
                pushNotificationService.send(payload.getPushToken(), payload.getSubject(), payload.getBody());
            }
            log.info("Email enviado a {}", payload.getTo());
            return true;
        } catch (MessagingException exception) {
            throw new IllegalStateException("No se pudo construir el email", exception);
        }
    }
}