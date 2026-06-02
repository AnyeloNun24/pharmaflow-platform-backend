package com.pharmaflow.auth_service.config;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Productor de Kafka usado por el relay del Outbox ({@code OutboxRelay}).
 * <p>
 * Clave y valor {@code String}: el relay envia el payload JSON ya serializado (que la outbox guardo),
 * con su discriminador {@code "type"} en el cuerpo. Hereda bootstrap-servers y la seguridad
 * (PLAINTEXT en dev, SASL_SSL en qa/prod) de {@code spring.kafka.*} via {@link KafkaProperties}.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(
                kafkaProperties.buildProducerProperties(null),
                new StringSerializer(),
                new StringSerializer()
        );
    }

    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }
}
