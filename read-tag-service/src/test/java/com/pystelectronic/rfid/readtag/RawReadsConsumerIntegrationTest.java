package com.pystelectronic.rfid.readtag;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.repository.ReadTagRepository;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class RawReadsConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rfid_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ReadTagRepository readTagRepository;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void dadoUnMensajeValido_cuandoSePublicaEnKafka_entoncesSePersisteEnReadTag() {
        // ARRANGE
        String epcPrueba = "E2806894000040038660A1FF";
        RawReadMessage mensaje = RawReadMessage.builder()
                .epc(epcPrueba)
                .tag("Producto de prueba")
                .tid("TID-TEST-001")
                .invTimes(3)
                .rssi(-65)
                .antId(1)
                .lastTime(Instant.now())
                .firstUpdate(Instant.now())
                .moduloId("GATE-TEST-01")
                .moduloRol("puerta1")
                .build();

        KafkaTemplate<String, RawReadMessage> producer = buildProducer();

        // ACT
        producer.send(new ProducerRecord<>("rfid.raw-reads", epcPrueba, mensaje));
        producer.flush();

        // ASSERT — espera hasta 20s para que el consumidor procese
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var resultado = readTagRepository.findByEpc(epcPrueba);
                    assertThat(resultado).isPresent();
                    assertThat(resultado.get().getModuloId()).isEqualTo("GATE-TEST-01");
                    assertThat(resultado.get().getRssi()).isEqualTo(-65);
                });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void dadoEPCDuplicado_cuandoSeLeeDoVeces_entoncesSoloExisteUnRegistro() {
        String epc = "E2806894000040038660B2EE";
        RawReadMessage primera = RawReadMessage.builder()
                .epc(epc).invTimes(1).rssi(-70)
                .lastTime(Instant.now()).firstUpdate(Instant.now())
                .moduloId("GATE-01").moduloRol("puerta1").build();
        RawReadMessage segunda = RawReadMessage.builder()
                .epc(epc).invTimes(2).rssi(-60)
                .lastTime(Instant.now()).firstUpdate(Instant.now())
                .moduloId("GATE-01").moduloRol("puerta1").build();

        KafkaTemplate<String, RawReadMessage> producer = buildProducer();
        producer.send(new ProducerRecord<>("rfid.raw-reads", epc, primera));
        producer.send(new ProducerRecord<>("rfid.raw-reads", epc, segunda));
        producer.flush();

        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    long count = readTagRepository.findAll().stream()
                            .filter(r -> epc.equals(r.getEpc())).count();
                    assertThat(count).isEqualTo(1);  // upsert: solo 1 registro
                    var tag = readTagRepository.findByEpc(epc).orElseThrow();
                    assertThat(tag.getRssi()).isEqualTo(-60);  // valor del segundo mensaje
                });
    }

    private KafkaTemplate<String, RawReadMessage> buildProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
