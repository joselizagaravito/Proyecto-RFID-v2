// src/kafka/messageClassifier.js
import { config } from '../config.js';
import { getActiveTransfer } from '../redis/redisClient.js';

/**
 * Clasifica un mensaje de rfid.validated en uno de tres tipos:
 *   - 'normal'   → epc:read
 *   - 'anomaly'  → epc:anomaly
 *   - 'portal'   → actualización de portal:state (siempre acompaña a normal/anomaly)
 *
 * Estrategia dual (campo explícito + validación Redis):
 *   1. Si status está en anomalyStatuses → anomalía directa (campo explícito)
 *   2. Si anomalyType tiene valor conocido → anomalía directa (campo explícito)
 *   3. Si status === 'VALID' pero no hay transfer activo en Redis → anomalía inferida
 *   4. Si status === 'VALID' y hay transfer activo → normal
 *
 * Esta estrategia detecta condiciones de carrera donde validation-service marcó VALID
 * pero el transfer expiró/cerró entre la validación y la recepción por realtime-service.
 */
export async function classifyMessage(message) {
  const { status, anomalyType, portalId } = message;

  // --- Paso 1: campo explícito tiene prioridad ---
  if (config.anomalyStatuses.has(status)) {
    return buildResult('anomaly', message, `status explícito: ${status}`);
  }

  if (anomalyType && config.anomalyTypes.has(anomalyType)) {
    return buildResult('anomaly', message, `anomalyType explícito: ${anomalyType}`);
  }

  // --- Paso 2: validación contra Redis para mensajes VALID ---
  if (status === 'VALID') {
    try {
      const activeTransfer = await getActiveTransfer(portalId);
      if (!activeTransfer) {
        return buildResult(
          'anomaly',
          message,
          'VALID pero sin transfer activo en Redis (condición de carrera o transfer expirado)',
          'NO_ACTIVE_TRANSFER'
        );
      }
      return buildResult('normal', message, 'VALID con transfer activo');
    } catch (err) {
      // Si Redis falla, no bloqueamos el flujo — tratamos como normal con advertencia
      console.warn(`[Classifier] Error consultando Redis para portalId=${portalId}: ${err.message}. Clasificando como normal.`);
      return buildResult('normal', message, 'VALID — Redis no disponible, sin validación secundaria');
    }
  }

  // --- Fallback: status desconocido → anomalía por precaución ---
  return buildResult('anomaly', message, `status desconocido: ${status}`, 'UNKNOWN_STATUS');
}

function buildResult(type, message, reason, inferredAnomalyType = null) {
  return {
    type,           // 'normal' | 'anomaly'
    message,        // mensaje original completo
    reason,         // para logging
    // Si es anomalía, aseguramos que anomalyType tenga valor
    anomalyType: type === 'anomaly'
      ? (message.anomalyType || inferredAnomalyType || 'UNCLASSIFIED')
      : null,
  };
}
