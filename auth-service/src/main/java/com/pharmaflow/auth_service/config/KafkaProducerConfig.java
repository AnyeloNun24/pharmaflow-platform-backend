package com.pharmaflow.auth_service.config;

import com.pharmaflow.auth_service.messaging.event.AuthDomainEvent;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, AuthDomainEvent> authEventProducerFactory(KafkaProperties kafkaProperties) {
        JsonSerializer<AuthDomainEvent> valueSerializer = new JsonSerializer<>();
        valueSerializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(
                kafkaProperties.buildProducerProperties(null),
                new StringSerializer(),
                valueSerializer
        );
    }

    @Bean
    public KafkaTemplate<String, AuthDomainEvent> authEventKafkaTemplate(ProducerFactory<String, AuthDomainEvent> authEventProducerFactory) {
        return new KafkaTemplate<>(authEventProducerFactory);
    }

}
