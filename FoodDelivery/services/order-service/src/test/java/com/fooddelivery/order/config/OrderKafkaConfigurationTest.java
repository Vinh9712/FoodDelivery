package com.fooddelivery.order.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts producer reliability defaults when set via test properties
 * (config-server YAML carries the same keys in non-test environments).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.kafka.producer.acks=all",
        "spring.kafka.producer.properties.enable.idempotence=true",
        "spring.kafka.producer.properties.max.in.flight.requests.per.connection=5"
})
class OrderKafkaConfigurationTest {

    @Autowired
    private ProducerFactory<String, Object> producerFactory;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void producerIsIdempotentWithAcksAll() {
        Map<String, Object> configs = producerFactory.getConfigurationProperties();
        assertThat(String.valueOf(configs.get(ProducerConfig.ACKS_CONFIG))).isEqualTo("all");
        Object idempotence = configs.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG);
        if (idempotence == null) {
            idempotence = configs.get("enable.idempotence");
        }
        assertThat(String.valueOf(idempotence)).isIn("true", "True");
    }
}
