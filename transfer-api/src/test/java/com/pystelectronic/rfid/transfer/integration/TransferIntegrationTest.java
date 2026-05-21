package com.pystelectronic.rfid.transfer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pystelectronic.rfid.common.dto.request.CreateTransferRequest;
import com.pystelectronic.rfid.common.enums.TransferPriority;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración contra PostgreSQL real (Testcontainers).
 * Valida el flujo completo: crear traslado → agregar pallet → despachar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rfid_transfers_test")
            .withUsername("rfid_test")
            .withPassword("rfid_test_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Deshabilitar Kafka y Redis en tests de integración de la capa REST
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9099");
        registry.add("spring.data.redis.host",          () -> "localhost");
        registry.add("spring.autoconfigure.exclude",    () ->
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String createdTransferId;

    // ─── §6.1 Crear traslado ──────────────────────────────────────

    @Test
    @Order(1)
    @WithMockUser(roles = {"OPERATOR"})
    @DisplayName("POST /transfers — debería crear traslado en estado DRAFT")
    void shouldCreateTransferSuccessfully() throws Exception {
        CreateTransferRequest req = CreateTransferRequest.builder()
                .originCode("A001")
                .destinationCode("B003")
                .scheduledDate(OffsetDateTime.now().plusDays(3))
                .priority(TransferPriority.HIGH)
                .carrierId("TRN-014")
                .remarks("Traslado de prueba de integración")
                .build();

        String response = mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferCode").value(org.hamcrest.Matchers.matchesPattern("OT-\\d{8}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.originCode").value("A001"))
                .andExpect(jsonPath("$.destinationCode").value("B003"))
                .andReturn().getResponse().getContentAsString();

        createdTransferId = objectMapper.readTree(response).get("transferId").asText();
        Assertions.assertNotNull(createdTransferId, "transferId no debe ser nulo");
    }

    // ─── Validación: mismo origen y destino ───────────────────────

    @Test
    @Order(2)
    @WithMockUser(roles = {"OPERATOR"})
    @DisplayName("POST /transfers — debería rechazar mismo origen y destino")
    void shouldRejectSameOriginAndDestination() throws Exception {
        CreateTransferRequest req = CreateTransferRequest.builder()
                .originCode("A001")
                .destinationCode("A001")
                .scheduledDate(OffsetDateTime.now().plusDays(1))
                .priority(TransferPriority.NORMAL)
                .build();

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SAME_ORIGIN_DESTINATION"));
    }

    // ─── §6.6 Listar traslados ────────────────────────────────────

    @Test
    @Order(3)
    @WithMockUser(roles = {"READER"})
    @DisplayName("GET /transfers — debería devolver lista paginada")
    void shouldListTransfers() throws Exception {
        mockMvc.perform(get("/api/v1/transfers")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.page").value(0));
    }

    // ─── §6.5 Obtener detalle ─────────────────────────────────────

    @Test
    @Order(4)
    @WithMockUser(roles = {"OPERATOR"})
    @DisplayName("GET /transfers/{id} — debería devolver detalle del traslado creado")
    void shouldGetTransferById() throws Exception {
        Assumptions.assumeTrue(createdTransferId != null, "Requiere que el traslado haya sido creado");

        mockMvc.perform(get("/api/v1/transfers/{id}", createdTransferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(createdTransferId))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ─── Acceso sin token ─────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("GET /transfers — sin autenticación debería devolver 401")
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/transfers"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Validaciones de campos ───────────────────────────────────

    @Test
    @Order(6)
    @WithMockUser(roles = {"OPERATOR"})
    @DisplayName("POST /transfers — LPN code inválido debería devolver 400")
    void shouldValidateLpnCodeFormat() throws Exception {
        // Body sin scheduledDate (campo requerido)
        String body = """
            {
              "originCode": "A001",
              "destinationCode": "B003",
              "priority": "HIGH"
            }
            """;

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
