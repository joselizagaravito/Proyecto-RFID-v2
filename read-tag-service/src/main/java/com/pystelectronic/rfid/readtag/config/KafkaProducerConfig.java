package com.pystelectronic.rfid.readtag.config;

import com.pystelectronic.rfid.readtag.dto.ValidatedReadEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del productor Kafka para el topic rfid.validated.
 *
 * Configuraciones clave:
 * - acks=all: el broker confirma escritura en todas las réplicas antes de
 *   responder → durabilidad garantizada, sin pérdida de mensajes.
 * - retries=3: reintenta hasta 3 veces ante fallos transitorios de red.
 * - idempotence=true: evita duplicados en caso de retry del productor.
 * - linger.ms=5: agrupa mensajes en ventanas de 5ms para mejor throughput
 *   con portales que disparan muchos EPCs seguidos.
 * - batch.size=16384: tamaño de lote en bytes (16 KB, valor estándar).
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, ValidatedReadEvent> validatedProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Durabilidad: requiere confirmación de todas las réplicas
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // Reintentos ante fallos transitorios
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Idempotencia: evita duplicados en retries
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Throughput: agrupa mensajes en ventanas de 5ms
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        // No incluir type headers — el consumidor conoce el tipo
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, ValidatedReadEvent> validatedKafkaTemplate() {
        return new KafkaTemplate<>(validatedProducerFactory());
    }
}
