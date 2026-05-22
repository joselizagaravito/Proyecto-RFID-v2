package com.pystelectronic.rfid.validation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Gestión del portal_state en Redis.
 *
 * Estructura de claves:
 *   portal:state:{deviceId}         → Hash con campos: transferId, readCount, expectedCount, lastReadAt
 *   transfer:active:{transferId}    → Hash con campos: transferCode, status, expectedLpns, readCount
 *
 * Según el spec:
 * - Cada EPC válido incrementa read_count en Redis (sub-ms).
 * - El snapshot Redis → PostgreSQL ocurre cada 30s vía @Scheduled.
 * - El realtime-service lee desde Redis, NO desde PostgreSQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalStateService {

    private final StringRedisTemplate redis;

    @Value("${rfid.redis.portal-state-prefix}")
    private String portalStatePrefix;

    @Value("${rfid.redis.transfer-state-prefix}")
    private String transferStatePrefix;

    @Value("${rfid.redis.portal-state-ttl}")
    private long portalStateTtlSeconds;

    // ────────────────────────────────────────────────
    // Portal State
    // ────────────────────────────────────────────────

    /**
     * Inicializa el estado de un portal cuando inicia una sesión de lectura.
     *
     * @param deviceId       ID del portal/dispositivo
     * @param transferId     ID del traslado activo asignado al portal
     * @param transferCode   Código de negocio del traslado
     * @param expectedCount  Total de LPNs esperados
     */
    public void initPortalState(
            String deviceId, String transferId,
            String transferCode, int expectedCount) {

        String key = portalStatePrefix + deviceId;
        Map<String, String> state = new HashMap<>();
        state.put("transferId", transferId);
        state.put("transferCode", transferCode);
        state.put("readCount", "0");
        state.put("expectedCount", String.valueOf(expectedCount));
        state.put("lastReadAt", String.valueOf(System.currentTimeMillis()));

        redis.opsForHash().putAll(key, state);
        redis.expire(key, Duration.ofSeconds(portalStateTtlSeconds));
        log.info("Portal state inicializado — deviceId={}, transferId={}, expectedCount={}",
                deviceId, transferId, expectedCount);
    }

    /**
     * Incrementa read_count en Redis para el portal dado.
     * Operación atómica (HINCRBY). Retorna el nuevo count.
     */
    public long incrementReadCount(String deviceId) {
        String key = portalStatePrefix + deviceId;
        Long newCount = redis.opsForHash().increment(key, "readCount", 1);
        redis.opsForHash().put(key, "lastReadAt",
                String.valueOf(System.currentTimeMillis()));
        return newCount != null ? newCount : 0L;
    }

    /**
     * Obtiene el estado completo del portal.
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, String>> getPortalState(String deviceId) {
        String key = portalStatePrefix + deviceId;
        Map<Object, Object> raw = redis.opsForHash().entries(key);
        if (raw.isEmpty()) return Optional.empty();
        Map<String, String> state = new HashMap<>();
        raw.forEach((k, v) -> state.put(k.toString(), v.toString()));
        return Optional.of(state);
    }

    // ────────────────────────────────────────────────
    // Transfer Active State
    // ────────────────────────────────────────────────

    /**
     * Actualiza el estado del traslado activo en Redis.
     * Se llama después de cada EPC validado.
     */
    public void updateTransferActiveState(
            String transferId, String transferCode,
            String status, int expectedLpns, long readCount) {

        String key = transferStatePrefix + transferId;
        Map<String, String> state = Map.of(
                "transferCode", transferCode,
                "status", status,
                "expectedLpns", String.valueOf(expectedLpns),
                "readCount", String.valueOf(readCount),
                "updatedAt", String.valueOf(System.currentTimeMillis())
        );
        redis.opsForHash().putAll(key, state);
        redis.expire(key, Duration.ofSeconds(portalStateTtlSeconds));
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, String>> getTransferActiveState(String transferId) {
        String key = transferStatePrefix + transferId;
        Map<Object, Object> raw = redis.opsForHash().entries(key);
        if (raw.isEmpty()) return Optional.empty();
        Map<String, String> state = new HashMap<>();
        raw.forEach((k, v) -> state.put(k.toString(), v.toString()));
        return Optional.of(state);
    }

    /**
     * Retorna todas las claves de portal_state activos.
     * Usado por el SnapshotScheduler para sincronizar a PostgreSQL.
     */
    public java.util.Set<String> getAllPortalStateKeys() {
        return redis.keys(portalStatePrefix + "*");
    }

    public java.util.Set<String> getAllTransferStateKeys() {
        return redis.keys(transferStatePrefix + "*");
    }
}
