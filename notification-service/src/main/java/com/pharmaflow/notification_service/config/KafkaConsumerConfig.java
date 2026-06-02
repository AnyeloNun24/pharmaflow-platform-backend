package com.pharmaflow.notification_service.config;

import com.pharmaflow.notification_service.messaging.event.AuthDomainEvent;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

@EnableKafka // Habilita los endpoints anotados con @KafkaListener que son creados internamente por un AbstractKafkaListenerContainerFactory
@Configuration
public class KafkaConsumerConfig {

    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    public ConsumerFactory<String, AuthDomainEvent> authConsumerFactory(KafkaProperties kafkaProperties) {
        JsonDeserializer<AuthDomainEvent> jsonDeserializer = new JsonDeserializer<>(AuthDomainEvent.class, false);
        jsonDeserializer.addTrustedPackages("com.pharmaflow.*");

        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer) // ErrorHandlingDeserializer envuelve al deserializador real
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuthDomainEvent> authListenerContainerFactory(
            ConsumerFactory<String, AuthDomainEvent> authConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, AuthDomainEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(authConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // Manejo de errores + Dead Letter Topic

    @Bean
    public ProducerFactory<String, Object> dltProducerFactory(KafkaProperties kafkaProperties) {
        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(Object.class, new JsonSerializer<>());

        return new DefaultKafkaProducerFactory<>(
                kafkaProperties.buildProducerProperties(null),
                new StringSerializer(),
                new DelegatingByTypeSerializer(delegates, true)
        );
    }

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate(ProducerFactory<String, Object> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    /**
     * Manejador de errores comun para los listeners de auth.
     * <p>
     * Estrategia: reintentar el procesamiento con backoff exponencial (1s, 2s, 4s) hasta 3 veces;
     * si sigue fallando, el {@link DeadLetterPublishingRecoverer} envia el evento a {@code <topic>.DLT}
     * y se confirma el offset, de modo que un fallo aislado no bloquea la particion ni se pierde.
     * <p>
     * Las excepciones de deserializacion ya estan en la lista "fatal" por defecto del
     * {@link DefaultErrorHandler}: no se reintentan y van directas al DLT.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1) // partition = -1: deja que Kafka elija particion en el DLT
        );

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        return new DefaultErrorHandler(recoverer, backOff);
    }

}
