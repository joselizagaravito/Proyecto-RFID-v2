package com.pystelectronic.rfid.transfer.service;

import com.pystelectronic.rfid.transfer.entity.PortalSession;
import com.pystelectronic.rfid.transfer.repository.PortalSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cierra automáticamente el pallet activo de sesiones inactivas.
 *
 * Si un portal no recibe lecturas durante {rfid.session.timeout-minutes}
 * minutos, el pallet activo se cierra (activePalletId=null). El traslado
 * sigue asignado, pero el siguiente LPN será rechazado hasta leer un pallet.
 *
 * Configurable vía application.yml:
 *   rfid.session.timeout-minutes: 10   (default)
 *   rfid.session.sweep-interval-ms: 60000 (cada minuto)
 *
 * Requiere @EnableScheduling en la aplicación.
 *
 * Sprint 9 · Pystelectronic
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PalletSessionExpiryScheduler {

    private final PortalSessionRepository sessionRepository;

    @Value("${rfid.session.timeout-minutes:10}")
    private long timeoutMinutes;

    @Scheduled(fixedDelayString = "${rfid.session.sweep-interval-ms:60000}")
    @Transactional
    public void expireInactiveSessions() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(timeoutMinutes);
        List<PortalSession> expired = sessionRepository.findExpiredSessions(threshold);

        if (expired.isEmpty()) return;

        for (PortalSession s : expired) {
            log.info("Sesión de pallet expirada por inactividad: portal={} pallet={} (último read: {})",
                    s.getPortalId(), s.getActivePalletId(), s.getLastReadAt());
            s.setActivePalletId(null);
            sessionRepository.save(s);
        }
        log.info("Sesiones de pallet expiradas: {}", expired.size());
    }
}
