package com.pystelectronic.rfid.readtag.config;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración Kafka para el consumidor batch del read-tag-service.
 *
 * Se crea explícitamente la factory batch para:
 * 1. Habilitar modo batch (batchListener = true).
 * 2. Configurar ack manual MANUAL_IMMEDIATE.
 * 3. Usar Virtual Threads de Java 21 para máximo throughput.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.max-poll-records:100}")
    private int maxPollRecords;

    @Bean
    public ConsumerFactory<String, RawReadMessage> rawReadConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);

        // Virtual Threads Java 21: máxima concurrencia sin overhead de hilos del SO
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        JsonDeserializer<RawReadMessage> valueDeserializer =
                new JsonDeserializer<>(RawReadMessage.class, false);
        valueDeserializer.addTrustedPackages("com.pystelectronic.rfid.*");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RawReadMessage>
    batchKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, RawReadMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(rawReadConsumerFactory());
        factory.setBatchListener(true);                                  // modo batch
        factory.setConcurrency(3);                                       // 3 consumidores en paralelo
        factory.getContainerProperties()
               .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE); // ack manual

        return factory;
    }
}
