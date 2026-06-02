package com.pharmaflow.notification_service.messaging.consumer;

import com.pharmaflow.notification_service.service.exception.EmailDeliveryException;
import com.pharmaflow.notification_service.service.interfaces.MailService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifica la resiliencia del consumo de eventos de auth: cuando el procesamiento de un evento
 * valido falla de forma persistente, el {@code DefaultErrorHandler} debe agotar los reintentos
 * y publicar el evento en el Dead Letter Topic ({@code auth.user.events.DLT}) en lugar de
 * perderlo o bloquear la particion.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {AuthEventConsumerDltIT.SOURCE_TOPIC, AuthEventConsumerDltIT.DLT_TOPIC}
)
class AuthEventConsumerDltIT {

    static final String SOURCE_TOPIC = "auth.user.events";
    static final String DLT_TOPIC = "auth.user.events.DLT";

    @Autowired
    private EmbeddedKafkaBroker broker;

    @MockitoBean
    private MailService mailService;

    @Test
    void evento_que_falla_en_procesamiento_termina_en_el_DLT() {
        doThrow(new EmailDeliveryException("SMTP caido (simulado en test)", new RuntimeException()))
                .when(mailService).sendHtml(anyString(), anyString(), anyString(), anyMap()); // Forzamos un fallo de procesamiento persistente: el envio de correo siempre revienta.

        String userCreatedJson = """
                {
                  "type": "USER_CREATED",
                  "userId": 1,
                  "email": "ada@pharmaflow.test",
                  "names": "Ada",
                  "surnames": "Lovelace",
                  "setPasswordToken": "tok-123"
                }
                """;

        publish(SOURCE_TOPIC, userCreatedJson);

        try (Consumer<String, String> dltConsumer = newStringConsumer("dlt-verifier")) {
            broker.consumeFromAnEmbeddedTopic(dltConsumer, DLT_TOPIC);

            // El backoff (1s + 2s + 4s) hace que la publicacion al DLT tarde ~7s: damos margen.
            ConsumerRecord<String, String> dltRecord =  KafkaTestUtils.getSingleRecord(dltConsumer, DLT_TOPIC, Duration.ofSeconds(20));

            assertThat(dltRecord).as("el evento fallido debe acabar en el DLT").isNotNull();
            assertThat(dltRecord.value()).contains("ada@pharmaflow.test");
        }

        // 1 intento original + 3 reintentos configurados = 4 invocaciones antes de rendirse al DLT.
        verify(mailService, times(4)).sendHtml(anyString(), anyString(), anyString(), anyMap());
    }

    private void publish(String topic, String value) {
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        try (Producer<String, String> producer = new DefaultKafkaProducerFactory<String, String>(props).createProducer()) {
            producer.send(new ProducerRecord<>(topic, value));
            producer.flush();
        }
    }

    private Consumer<String, String> newStringConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", broker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
