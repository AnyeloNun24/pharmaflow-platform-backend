package com.pharmaflow.notification_service.service.implementation;

import com.pharmaflow.notification_service.config.properties.NotificationMailProperties;
import com.pharmaflow.notification_service.service.exception.EmailDeliveryException;
import com.pharmaflow.notification_service.service.interfaces.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Implementacion de {@link MailService} basada en la integracion de correo de Spring
 * ({@link JavaMailSender} + {@link MimeMessageHelper}) y plantillas HTML de Thymeleaf.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final SpringTemplateEngine templateEngine;
    private final JavaMailSender mailSender;
    private final NotificationMailProperties mailProperties;

    @Override
    public void sendHtml(String to, String subject, String template, Map<String, Object> variables) {
        try {
            String htmlBody = renderTemplate(template, variables);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(mailProperties.from(), mailProperties.appName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            log.info("Correo enviado a={} asunto='{}' plantilla='{}'", to, subject, template);

        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new EmailDeliveryException("No se pudo construir el correo para %s con la plantilla %s".formatted(to, template), e);
        } catch (MailException e) {
            throw new EmailDeliveryException("Fallo la entrega SMTP del correo para %s".formatted(to), e);
        }
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(template, context); // Renderizar plantilla
    }

}
