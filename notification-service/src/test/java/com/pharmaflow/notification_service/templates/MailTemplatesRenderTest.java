package com.pharmaflow.notification_service.templates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que las plantillas de correo renderizan sin errores de sintaxis Thymeleaf
 * y producen el contenido esperado. Usa el {@link SpringTemplateEngine} real autoconfigurado
 * (mismo motor y dialecto SpringEL que en produccion) para que la prueba sea fiel al envio.
 * <p>
 * El listener de Kafka se desactiva ({@code auto-startup=false}) porque esta prueba solo
 * ejercita el renderizado de plantillas y no necesita un broker.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class MailTemplatesRenderTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void welcome_renderiza_con_nombre_y_anio() {
        String html = render("mail/welcome", Map.of(
                "appName", "PharmaFlow",
                "fullName", "Ada Lovelace",
                "setPasswordUrl", "https://app.test/auth/set-password?token=abc",
                "currentYear", 2026
        ));

        assertThat(html)
                .contains("Hola, Ada Lovelace")
                .contains("https://app.test/auth/set-password?token=abc")
                .contains("© 2026 PharmaFlow")
                .doesNotContain("${"); // ninguna expresion quedo sin resolver
    }

    @Test
    void welcome_usa_saludo_generico_si_no_hay_nombre() {
        String html = render("mail/welcome", Map.of(
                "appName", "PharmaFlow",
                "fullName", "",
                "setPasswordUrl", "https://app.test/auth/set-password?token=abc",
                "currentYear", 2026
        ));

        assertThat(html).contains("Te damos la bienvenida");
    }

    @Test
    void passwordReset_renderiza_con_enlace_y_aviso_de_seguridad() {
        String html = render("mail/password-reset", Map.of(
                "appName", "PharmaFlow",
                "name", "Ada",
                "resetUrl", "https://app.test/auth/reset-password?token=xyz",
                "currentYear", 2026
        ));

        assertThat(html)
                .contains("Hola, Ada")
                .contains("https://app.test/auth/reset-password?token=xyz")
                .contains("caducará")
                .contains("© 2026 PharmaFlow")
                .doesNotContain("${");
    }

    private String render(String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(template, context);
    }
}
