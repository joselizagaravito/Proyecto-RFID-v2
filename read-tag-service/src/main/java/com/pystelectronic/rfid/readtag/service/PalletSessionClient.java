package com.pystelectronic.rfid.readtag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente HTTP para invocar el endpoint interno de sesión de pallet.
 * Si el LPN es rechazado (sin pallet activo), reintenta hasta 3 veces
 * con 500ms de espera — el tag de pallet de la misma ráfaga llegará
 * en ese intervalo y el reintento tendrá éxito.
 *
 * Sprint 9 · Pystelectronic
 */
@Slf4j
@Service
public class PalletSessionClient {

    @Value("${rfid.session.transfer-api-url:http://transfer-api:8080}")
    private String transferApiUrl;

    private static final int MAX_RETRIES   = 3;
    private static final long RETRY_DELAY_MS = 500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Async
    public void notifySession(String portalId, String epc) {
        if (portalId == null || portalId.isBlank() || epc == null || epc.isBlank()) return;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String url  = transferApiUrl
                        + "/api/v1/internal/portals/" + portalId + "/session/read";
                String body = "{\"epc\":\"" + epc + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(3))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                String body2 = response.body();
                log.debug("[PalletSession] intento={} epc={} portal={} → HTTP {} body={}",
                        attempt, epc, portalId, response.statusCode(), body2);

                // Si fue exitoso o es un pallet (no LPN rechazado) → terminar
                if (response.statusCode() == 200 && !body2.contains("LPN_REJECTED")) {
                    return;
                }

                // LPN_REJECTED → esperar y reintentar
                if (body2.contains("LPN_REJECTED") && attempt < MAX_RETRIES) {
                    log.debug("[PalletSession] LPN_REJECTED epc={} — reintentando en {}ms (intento {}/{})",
                            epc, RETRY_DELAY_MS, attempt, MAX_RETRIES);
                    Thread.sleep(RETRY_DELAY_MS);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("[PalletSession] Error en intento {} para epc={}: {}",
                        attempt, epc, e.getMessage());
            }
        }
        log.warn("[PalletSession] epc={} portal={} — no se pudo asociar tras {} intentos",
                epc, portalId, MAX_RETRIES);
    }
}
