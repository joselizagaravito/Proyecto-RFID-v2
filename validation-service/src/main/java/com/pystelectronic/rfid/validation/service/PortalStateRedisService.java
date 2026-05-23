package com.pystelectronic.rfid.validation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pystelectronic.rfid.validation.domain.PortalState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Gestiona el estado de cada portal RFID en Redis.
 *
 * Esquema de claves:
 *   portal:state:{portalId}  →  JSON de PortalState
 *   portal:lpn:{epc}         →  "{transferId}|{lpnCode}|{skuCode}"  (caché de lookup)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalStateRedisService {

    private static final String STATE_KEY_PREFIX = "portal:state:";
    private static final String LPN_CACHE_PREFIX = "portal:lpn:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ── Leer estado ────────────────────────────────────────────

    public Optional<PortalState> getPortalState(String portalId) {
        String key = STATE_KEY_PREFIX + portalId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            log.debug("portal:state:{} no encontrado en Redis", portalId);
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PortalState.class));
        } catch (Exception e) {
            log.error("Error deserializando portal:state:{} → {}", portalId, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Actualizar estado tras una lectura válida ──────────────

    public void recordValidRead(String portalId, Instant readAt) {
        getPortalState(portalId).ifPresentOrElse(
                state -> {
                    state.incrementRead(readAt);
                    saveState(state);
                },
                () -> log.warn("recordValidRead: portal {} sin estado en Redis", portalId)
        );
    }

    // ── Actualizar estado tras una anomalía ────────────────────

    public void recordAnomaly(String portalId, Instant readAt) {
        getPortalState(portalId).ifPresentOrElse(
                state -> {
                    state.incrementAnomaly(readAt);
                    saveState(state);
                },
                () -> log.warn("recordAnomaly: portal {} sin estado en Redis", portalId)
        );
    }

    // ── Caché de lookup EPC → transferId|lpnCode|skuCode ──────

    /**
     * Busca en la caché Redis si el EPC ya fue resuelto previamente.
     * Evita ir a PostgreSQL en cada lectura repetida del mismo tag.
     */
    public Optional<String[]> getCachedLpn(String epc) {
        String val = redisTemplate.opsForValue().get(LPN_CACHE_PREFIX + epc);
        if (val == null) return Optional.empty();
        return Optional.of(val.split("\\|", 3));  // [transferId, lpnCode, skuCode]
    }

    /**
     * Guarda en caché el resultado de la búsqueda de un EPC.
     * TTL: 2 horas (un traslado no dura más que eso sin actividad).
     */
    public void cacheLpn(String epc, String transferId, String lpnCode, String skuCode) {
        String val = transferId + "|" + lpnCode + "|" + skuCode;
        redisTemplate.opsForValue().set(
                LPN_CACHE_PREFIX + epc, val,
                java.time.Duration.ofHours(2)
        );
    }

    public void evictLpnCache(String epc) {
        redisTemplate.delete(LPN_CACHE_PREFIX + epc);
    }

    // ── Interno ────────────────────────────────────────────────

    private void saveState(PortalState state) {
        try {
            String key  = STATE_KEY_PREFIX + state.getPortalId();
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, json);
        } catch (Exception e) {
            log.error("Error serializando portal:state:{} → {}", state.getPortalId(), e.getMessage());
        }
    }
}
