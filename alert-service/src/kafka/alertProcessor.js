// src/kafka/alertProcessor.js
// Recibe mensajes de transfer.alerts y transfer.events,
// construye el payload del webhook según la spec (sección 13.2)
// y llama al webhookSender.

import { randomUUID } from 'crypto';
import { z } from 'zod';
import { config } from '../config.js';
import { logger } from '../utils/logger.js';
import { insertAlertLog } from '../db/dbClient.js';
import { sendWebhook } from '../webhook/webhookSender.js';

// ── Esquemas de validación con Zod ────────────────────────────────────────────

// Mensaje de transfer.alerts (producido por validation-service)
const TransferAlertSchema = z.object({
  alertType:       z.enum(['EXTRA_LPN', 'UNREGISTERED_EPC', 'QUANTITY_MISMATCH']),
  transferId:      z.string().optional(),
  transferCode:    z.string().optional(),
  portalId:        z.string().optional(),
  portalLocation:  z.string().optional(),
  epc:             z.string().optional(),
  lpnCode:         z.string().optional(),
  skuCode:         z.string().optional(),
  expectedQuantity:z.number().optional(),
  receivedQuantity:z.number().optional(),
  deviceId:        z.string().optional(),
  correlationId:   z.string().optional(),
  detectedAt:      z.string().optional(),
}).passthrough();

// Mensaje de transfer.events que puede disparar PALLET_INCOMPLETE o MISSING_LPN
const TransferEventSchema = z.object({
  eventType:       z.string(),
  transferId:      z.string().optional(),
  transferCode:    z.string().optional(),
  portalId:        z.string().optional(),
  portalLocation:  z.string().optional(),
  expectedLpns:    z.number().optional(),
  readLpns:        z.number().optional(),
  missingLpns:     z.array(z.string()).optional(),
  extraEpcs:       z.array(z.string()).optional(),
  correlationId:   z.string().optional(),
  detectedAt:      z.string().optional(),
}).passthrough();

// ── Procesador principal ──────────────────────────────────────────────────────

/**
 * Procesa un mensaje de transfer.alerts.
 * Tipos manejados: EXTRA_LPN, UNREGISTERED_EPC, QUANTITY_MISMATCH
 */
export async function processTransferAlert(rawMessage) {
  let parsed;
  try {
    const data = JSON.parse(rawMessage);
    parsed = TransferAlertSchema.parse(data);
  } catch (err) {
    logger.warn({ err, rawMessage }, '[AlertProcessor] Mensaje transfer.alerts inválido — se descarta');
    return;
  }

  if (!config.anomalyTypes.has(parsed.alertType)) {
    logger.debug({ alertType: parsed.alertType }, '[AlertProcessor] alertType no reconocido — se ignora');
    return;
  }

  const alertId = randomUUID();
  const detectedAt = parsed.detectedAt || new Date().toISOString();

  const payload = buildPayload({
    alertId,
    alertType:        parsed.alertType,
    transferId:       parsed.transferId,
    transferCode:     parsed.transferCode,
    portalId:         parsed.portalId,
    portalLocation:   parsed.portalLocation,
    expectedLpns:     null,
    readLpns:         null,
    missingLpns:      [],
    extraEpcs:        parsed.epc ? [parsed.epc] : [],
    detectedAt,
    correlationId:    parsed.correlationId,
  });

  // Persistir ANTES del webhook (idempotencia)
  await insertAlertLog({
    alertId,
    transferId:       parsed.transferId,
    alertType:        parsed.alertType,
    lpnCode:          parsed.lpnCode,
    epc:              parsed.epc,
    skuCode:          parsed.skuCode,
    expectedQuantity: parsed.expectedQuantity,
    receivedQuantity: parsed.receivedQuantity,
    deviceId:         parsed.deviceId,
    portalLocation:   parsed.portalLocation,
    correlationId:    parsed.correlationId,
    webhookStatus:    null,
    retryCount:       0,
    deliveredAt:      null,
  });

  logger.info({
    alertId,
    alertType:   parsed.alertType,
    transferId:  parsed.transferId,
    correlationId: parsed.correlationId,
  }, '[AlertProcessor] Anomalía detectada — enviando webhook');

  await sendWebhook(payload);
}

/**
 * Procesa un mensaje de transfer.events.
 * Tipos que disparan alerta: DISPATCH_CONFIRMED (→ PALLET_INCOMPLETE / MISSING_LPN)
 */
export async function processTransferEvent(rawMessage) {
  let parsed;
  try {
    const data = JSON.parse(rawMessage);
    parsed = TransferEventSchema.parse(data);
  } catch (err) {
    logger.warn({ err }, '[AlertProcessor] Mensaje transfer.events inválido — se descarta');
    return;
  }

  // Solo eventos de confirmación de despacho pueden disparar estas anomalías
  if (parsed.eventType !== 'DISPATCH_CONFIRMED' && parsed.eventType !== 'PORTAL_CLOSED') {
    return;
  }

  const readLpns     = parsed.readLpns     ?? 0;
  const expectedLpns = parsed.expectedLpns ?? 0;
  const missingLpns  = parsed.missingLpns  ?? [];

  // PALLET_INCOMPLETE: read_count < expected_lpns
  if (expectedLpns > 0 && readLpns < expectedLpns) {
    await emitEvent({
      alertType:     'PALLET_INCOMPLETE',
      transferId:    parsed.transferId,
      transferCode:  parsed.transferCode,
      portalId:      parsed.portalId,
      portalLocation:parsed.portalLocation,
      expectedLpns,
      readLpns,
      missingLpns,
      extraEpcs:     [],
      correlationId: parsed.correlationId,
      detectedAt:    parsed.detectedAt,
    });
  }

  // MISSING_LPN: hay LPNs específicos que no fueron leídos
  if (missingLpns.length > 0) {
    for (const lpnCode of missingLpns) {
      await emitEvent({
        alertType:     'MISSING_LPN',
        transferId:    parsed.transferId,
        transferCode:  parsed.transferCode,
        portalId:      parsed.portalId,
        portalLocation:parsed.portalLocation,
        expectedLpns,
        readLpns,
        missingLpns:   [lpnCode],
        extraEpcs:     [],
        correlationId: parsed.correlationId,
        detectedAt:    parsed.detectedAt,
        lpnCode,
      });
    }
  }
}

// ── Helpers internos ──────────────────────────────────────────────────────────

async function emitEvent(data) {
  const alertId    = randomUUID();
  const detectedAt = data.detectedAt || new Date().toISOString();

  const payload = buildPayload({ alertId, ...data, detectedAt });

  await insertAlertLog({
    alertId,
    transferId:       data.transferId,
    alertType:        data.alertType,
    lpnCode:          data.lpnCode || null,
    epc:              null,
    skuCode:          null,
    expectedQuantity: data.expectedLpns,
    receivedQuantity: data.readLpns,
    deviceId:         null,
    portalLocation:   data.portalLocation,
    correlationId:    data.correlationId,
    webhookStatus:    null,
    retryCount:       0,
    deliveredAt:      null,
  });

  logger.info({
    alertId,
    alertType:    data.alertType,
    transferId:   data.transferId,
    correlationId:data.correlationId,
  }, '[AlertProcessor] Anomalía de evento — enviando webhook');

  await sendWebhook(payload);
}

/**
 * Construye el payload JSON del webhook según la spec (sección 13.2).
 */
function buildPayload(data) {
  return {
    alertId:        data.alertId,
    alertType:      data.alertType,
    transferId:     data.transferId     || null,
    transferCode:   data.transferCode   || null,
    portalId:       data.portalId       || null,
    portalLocation: data.portalLocation || null,
    expectedLpns:   data.expectedLpns   ?? null,
    readLpns:       data.readLpns       ?? null,
    missingLpns:    data.missingLpns    || [],
    extraEpcs:      data.extraEpcs      || [],
    detectedAt:     data.detectedAt,
    correlationId:  data.correlationId  || null,
  };
}
