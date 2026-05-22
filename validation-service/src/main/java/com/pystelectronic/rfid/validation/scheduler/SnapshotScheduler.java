package com.pystelectronic.rfid.validation.scheduler;

import com.pystelectronic.rfid.validation.service.PortalStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Scheduler de sincronización: portal_state Redis → PostgreSQL.
 *
 * Ejecuta cada 30 segundos (configurable vía rfid.snapshot.interval-ms).
 * Garantiza recuperación ante fallo de Redis: si Redis cae y se reinicia,
 * el estado persistido en PostgreSQL sirve como punto de restauración.
 *
 * La tabla portal_state_snapshot debe existir en la BD:
 *
 *   CREATE TABLE portal_state_snapshot (
 *       device_id       VARCHAR(40) PRIMARY KEY,
 *       transfer_id     VARCHAR(36),
 *       transfer_code   VARCHAR(30),
 *       read_count      INTEGER DEFAULT 0,
 *       expected_count  INTEGER DEFAULT 0,
 *       last_read_at    BIGINT,
 *       synced_at       TIMESTAMPTZ DEFAULT NOW()
 *   );
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotScheduler {

    private final PortalStateService portalStateService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${rfid.redis.portal-state-prefix}")
    private String portalStatePrefix;

    /**
     * Sincroniza todos los portal_state activos de Redis a PostgreSQL.
     * fixedDelayString espera que el intervalo anterior termine antes de
     * iniciar el siguiente (evita solapamiento si la BD está lenta).
     */
    @Scheduled(fixedDelayString = "${rfid.snapshot.interval-ms:30000}")
    public void syncPortalStatesToPostgres() {
        Set<String> keys = portalStateService.getAllPortalStateKeys();
        if (keys == null || keys.isEmpty()) return;

        log.debug("Snapshot Redis→PG iniciado: {} portales activos", keys.size());
        int synced = 0;

        for (String key : keys) {
            String deviceId = key.replace(portalStatePrefix, "");
            try {
                portalStateService.getPortalState(deviceId).ifPresent(state -> {
                    upsertPortalSnapshot(deviceId, state);
                });
                synced++;
            } catch (Exception ex) {
                log.error("Error sincronizando portal_state para deviceId={}: {}",
                        deviceId, ex.getMessage());
            }
        }

        log.debug("Snapshot Redis→PG completado: {}/{} portales sincronizados",
                synced, keys.size());
    }

    private void upsertPortalSnapshot(String deviceId, Map<String, String> state) {
        String sql = """
                INSERT INTO portal_state_snapshot
                    (device_id, transfer_id, transfer_code,
                     read_count, expected_count, last_read_at, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (device_id) DO UPDATE SET
                    transfer_id    = EXCLUDED.transfer_id,
                    transfer_code  = EXCLUDED.transfer_code,
                    read_count     = EXCLUDED.read_count,
                    expected_count = EXCLUDED.expected_count,
                    last_read_at   = EXCLUDED.last_read_at,
                    synced_at      = NOW()
                """;

        jdbcTemplate.update(sql,
                deviceId,
                state.getOrDefault("transferId", null),
                state.getOrDefault("transferCode", null),
                parseIntSafe(state.get("readCount")),
                parseIntSafe(state.get("expectedCount")),
                parseLongSafe(state.get("lastReadAt"))
        );
    }

    private int parseIntSafe(String value) {
        try { return value != null ? Integer.parseInt(value) : 0; }
        catch (NumberFormatException e) { return 0; }
    }

    private long parseLongSafe(String value) {
        try { return value != null ? Long.parseLong(value) : 0L; }
        catch (NumberFormatException e) { return 0L; }
    }
}
