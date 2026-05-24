// src/webhook/webhookSender.js
import crypto from 'crypto';
import axios from 'axios';
import { config } from '../config.js';
import { logger } from '../utils/logger.js';
import { updateAlertWebhookResult } from '../db/dbClient.js';

/**
 * Genera la firma HMAC-SHA256 del cuerpo JSON.
 * El WMS/ERP verifica esta firma con la misma clave compartida.
 */
function buildSignature(bodyStr) {
  return crypto
    .createHmac('sha256', config.webhook.hmacSecret)
    .update(bodyStr)
    .digest('hex');
}

/**
 * Envía la alerta al webhook con reintentos y backoff exponencial.
 * Persiste el resultado final en alert_log.
 *
 * @param {object} payload  Payload JSON del webhook (spec sección 13.2)
 * @returns {Promise<{success: boolean, statusCode: number|null, attempts: number}>}
 */
export async function sendWebhook(payload) {
  const bodyStr   = JSON.stringify(payload);
  const signature = buildSignature(bodyStr);
  const timestamp = new Date().toISOString();

  const headers = {
    'Content-Type':       'application/json',
    'X-Rfid-Alert-Id':    payload.alertId,
    'X-Rfid-Signature':   signature,
    'X-Rfid-Timestamp':   timestamp,
  };

  let lastStatus = null;
  let attempt    = 0;

  while (attempt < config.webhook.maxRetries) {
    attempt++;
    try {
      const response = await axios.post(config.webhook.url, bodyStr, {
        headers,
        timeout: config.webhook.timeoutMs,
        validateStatus: () => true, // manejar todos los códigos manualmente
      });

      lastStatus = response.status;

      if (response.status === 200 || response.status === 202) {
        // ── Entrega exitosa ────────────────────────────────
        const deliveredAt = new Date().toISOString();
        await updateAlertWebhookResult(payload.alertId, lastStatus, attempt, deliveredAt);

        logger.info({
          alertId:      payload.alertId,
          alertType:    payload.alertType,
          webhookStatus: lastStatus,
          attempt,
        }, '[Webhook] Alerta entregada exitosamente');

        return { success: true, statusCode: lastStatus, attempts: attempt };
      }

      if (response.status >= 400 && response.status < 500) {
        // ── Error de cliente — no reintentar ──────────────
        logger.error({
          alertId:   payload.alertId,
          alertType: payload.alertType,
          status:    lastStatus,
        }, '[Webhook] Error de cliente (4xx) — no se reintenta');

        await updateAlertWebhookResult(payload.alertId, lastStatus, attempt, null);
        return { success: false, statusCode: lastStatus, attempts: attempt };
      }

      // ── 5xx — reintentable ─────────────────────────────
      logger.warn({
        alertId:   payload.alertId,
        alertType: payload.alertType,
        status:    lastStatus,
        attempt,
      }, `[Webhook] Error 5xx — intento ${attempt}/${config.webhook.maxRetries}`);

    } catch (err) {
      // Timeout o error de red
      logger.warn({
        alertId:   payload.alertId,
        alertType: payload.alertType,
        attempt,
        error:     err.message,
      }, `[Webhook] Error de red/timeout — intento ${attempt}/${config.webhook.maxRetries}`);
      lastStatus = null;
    }

    // Esperar antes del siguiente reintento (si queda alguno)
    if (attempt < config.webhook.maxRetries) {
      const delayMs = config.webhook.backoffMs[attempt - 1] || 9000;
      logger.debug({ delayMs, attempt }, '[Webhook] Esperando antes de reintentar');
      await new Promise(resolve => setTimeout(resolve, delayMs));
    }
  }

  // ── Todos los intentos fallaron ────────────────────────
  await updateAlertWebhookResult(payload.alertId, lastStatus ?? 500, attempt, null);

  logger.error({
    alertId:   payload.alertId,
    alertType: payload.alertType,
    attempts:  attempt,
    lastStatus,
  }, '[Webhook] Todos los reintentos fallaron — alerta no entregada');

  return { success: false, statusCode: lastStatus, attempts: attempt };
}
