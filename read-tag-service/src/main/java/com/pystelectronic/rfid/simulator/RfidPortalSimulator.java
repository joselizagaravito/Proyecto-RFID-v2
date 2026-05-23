package com.pystelectronic.rfid.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Simulador de portal RFID para pruebas del Sprint 4.
 *
 * Publica EPCs del pallet de referencia PL954001302868 al topic rfid.raw-reads.
 * Útil para probar el pipeline completo sin hardware físico.
 *
 * CÓMO EJECUTAR (desde la carpeta rfid-backend):
 *
 *   mvn exec:java \
 *     -pl read-tag-service \
 *     -Dexec.mainClass="com.pystelectronic.rfid.simulator.RfidPortalSimulator" \
 *     -Dexec.args="--bootstrap localhost:9092 --count 10 --interval 500 --mode kafka"
 *
 * O en modo REST (llama directamente al endpoint del servicio):
 *   -Dexec.args="--mode rest --url http://localhost:8082/api/v1/read-tags/batch"
 *
 * Parámetros configurables:
 *   --bootstrap  Kafka bootstrap servers (default: localhost:9092)
 *   --count      Número de ciclos de lectura (default: 5)
 *   --interval   Milisegundos entre ciclos (default: 1000)
 *   --deviceId   ID del portal simulado (default: PORTAL-SIM-01)
 *   --mode       kafka | rest (default: kafka)
 *   --url        URL base del servicio REST (solo modo rest)
 */
public class RfidPortalSimulator {

    /** EPCs reales del pallet PL954001302868 — caso de prueba oficial del sistema */
    private static final List<String> EPC_POOL = List.of(
        "E2806894000040038660A111",  // PAN STAR N°18 GRAY — LPN 99950000272607
        "E2806894000040038660A112",
        "E2806894000040038660A113",
        "E2806894000040038660A114",
        "E2806894000040038660A115",
        "E2806894000040038660A116",
        "E2806894000040038660A117",
        "E2806894000040038660A118",
        "E2806894000040038660A119",
        "E2806894000040038660A11A",
        "E2806894000040038660A11B",
        "E2806894000040038660A11C",  // POT GRISANTIA N22 — LPN 99950000272614
        "E2806894000040038660B201",  // GRANITE SINK 36X40 — LPN 99950000272621
        "E2806894000040038660B202",
        "E2806894000040038660B203",
        "E2806894000040038660B204",
        "E2806894000040038660B205",
        "E2806894000040038660C301",  // PAN BLACK INDUCTION N22 — LPN 99950000272638
        "E2806894000040038660D401",  // PAN BLACK INDUCTION N26 — LPN 99950000272645
        "E2806894000040038660E501"   // LOOSE_ITEM CAST IRON PAN N16
    );

    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);

        String bootstrapServers = params.getOrDefault("bootstrap", "localhost:9092");
        int count               = Integer.parseInt(params.getOrDefault("count", "5"));
        long intervalMs         = Long.parseLong(params.getOrDefault("interval", "1000"));
        String deviceId         = params.getOrDefault("deviceId", "PORTAL-SIM-01");
        String mode             = params.getOrDefault("mode", "kafka");

        System.out.printf("""
            ╔══════════════════════════════════════════════════════╗
            ║         Simulador de Portal RFID — Sprint 4          ║
            ║  Pallet: PL954001302868 · %d EPCs disponibles        ║
            ╚══════════════════════════════════════════════════════╝
            Modo: %s | Device: %s | Ciclos: %d | Intervalo: %dms%n
            """, EPC_POOL.size(), mode.toUpperCase(), deviceId, count, intervalMs);

        if ("rest".equalsIgnoreCase(mode)) {
            runRestMode(params, count, intervalMs, deviceId);
        } else {
            runKafkaMode(bootstrapServers, count, intervalMs, deviceId);
        }
    }

    // ── Modo Kafka ────────────────────────────────────────────────────────────

    private static void runKafkaMode(
            String bootstrapServers, int count, long intervalMs, String deviceId)
            throws Exception {

        ObjectMapper mapper = buildMapper();
        Properties props = buildProducerProps(bootstrapServers);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int cycle = 1; cycle <= count; cycle++) {
                String correlationId = "SIM-" + UUID.randomUUID().toString().substring(0, 8);
                int batchSize = 3 + new Random().nextInt(5); // 3-7 EPCs por ciclo

                System.out.printf("[Ciclo %d/%d] correlationId=%s EPCs=%d%n",
                        cycle, count, correlationId, batchSize);

                for (int i = 0; i < batchSize; i++) {
                    String epc = EPC_POOL.get(new Random().nextInt(EPC_POOL.size()));
                    Map<String, Object> message = buildMessage(epc, deviceId);
                    String json = mapper.writeValueAsString(message);

                    ProducerRecord<String, String> record =
                            new ProducerRecord<>("rfid.raw-reads", epc, json);
                    record.headers().add(new RecordHeader(
                            "X-Correlation-Id",
                            correlationId.getBytes(StandardCharsets.UTF_8)));

                    producer.send(record, (metadata, ex) -> {
                        if (ex != null) {
                            System.err.println("  ERROR publicando EPC=" + epc + ": " + ex.getMessage());
                        } else {
                            System.out.printf("  ✓ EPC=%s → partition=%d offset=%d%n",
                                    epc, metadata.partition(), metadata.offset());
                        }
                    });
                }

                producer.flush();
                if (cycle < count) Thread.sleep(intervalMs);
            }
        }

        System.out.println("\n✅ Simulación completada. Verifica en Kafka UI: http://localhost:8090");
    }

    // ── Modo REST ─────────────────────────────────────────────────────────────

    private static void runRestMode(
            Map<String, String> params, int count, long intervalMs, String deviceId)
            throws Exception {

        String baseUrl = params.getOrDefault("url", "http://localhost:8082/api/v1/read-tags/batch");
        ObjectMapper mapper = buildMapper();

        System.out.println("Enviando lotes a: " + baseUrl);

        for (int cycle = 1; cycle <= count; cycle++) {
            String correlationId = "SIM-REST-" + UUID.randomUUID().toString().substring(0, 8);
            int batchSize = 3 + new Random().nextInt(5);

            List<Map<String, Object>> batch = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                String epc = EPC_POOL.get(new Random().nextInt(EPC_POOL.size()));
                batch.add(buildMessage(epc, deviceId));
            }

            String body = mapper.writeValueAsString(batch);

            ProcessBuilder pb = new ProcessBuilder(
                "curl", "-s", "-w", "\nHTTP_STATUS:%{http_code}",
                "-X", "POST", baseUrl,
                "-H", "Content-Type: application/json",
                "-H", "X-Correlation-Id: " + correlationId,
                "-d", body
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String response = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();

            System.out.printf("[Ciclo %d/%d] corrId=%s batch=%d → %s%n",
                    cycle, count, correlationId, batchSize, response.trim());

            if (cycle < count) Thread.sleep(intervalMs);
        }

        System.out.println("\n✅ Simulación REST completada.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> buildMessage(String epc, String deviceId) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("epc",        epc);
        msg.put("tag",        "RFID-TAG-" + epc.substring(20));
        msg.put("invTimes",   1 + new Random().nextInt(5));
        msg.put("rssi",       -50 - new Random().nextInt(30));  // -50 a -80 dBm
        msg.put("antId",      1 + new Random().nextInt(4));
        msg.put("lastTime",   Instant.now().toString());
        msg.put("firstUpdate", Instant.now().minusSeconds(30).toString());
        msg.put("moduloId",   deviceId);
        msg.put("moduloRol",  "puerta1");
        msg.put("color",      "GREEN");
        return msg;
    }

    private static Properties buildProducerProps(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 2);
        return props;
    }

    private static ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            if (args[i].startsWith("--")) {
                params.put(args[i].substring(2), args[i + 1]);
            }
        }
        return params;
    }
}
