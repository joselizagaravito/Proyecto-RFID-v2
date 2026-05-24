// src/socket/socketServer.js
import { Server } from 'socket.io';
import { config } from '../config.js';
import { getAllPortalStates, getPortalState } from '../redis/redisClient.js';

let io = null;
let syncInterval = null;

/**
 * Inicializa el servidor Socket.IO sobre el servidor HTTP de Express.
 * Namespace: /rfid
 * @param {import('http').Server} httpServer
 * @returns {import('socket.io').Server}
 */
export function initSocketServer(httpServer) {
  io = new Server(httpServer, {
    cors: config.socket.cors,
    // Path por defecto /socket.io — compatible con el cliente del frontend
    transports: ['websocket', 'polling'],
  });

  const rfid = io.of(config.socket.namespace);

  rfid.on('connection', async (socket) => {
    console.info(`[Socket.IO] Cliente conectado: ${socket.id} | IP: ${socket.handshake.address}`);

    // --- On-connect snapshot: enviar estado actual de todos los portales ---
    try {
      const portalStates = await getAllPortalStates();
      if (portalStates.length > 0) {
        socket.emit('portal:state', {
          type: 'snapshot',
          portals: portalStates,
          timestamp: new Date().toISOString(),
        });
        console.debug(`[Socket.IO] Snapshot enviado a ${socket.id}: ${portalStates.length} portales`);
      }
    } catch (err) {
      console.error('[Socket.IO] Error enviando snapshot inicial:', err.message);
    }

    socket.on('disconnect', (reason) => {
      console.info(`[Socket.IO] Cliente desconectado: ${socket.id} | razón: ${reason}`);
    });

    // Permitir que el frontend solicite explícitamente el estado de un portal
    socket.on('portal:request', async ({ portalId }) => {
      if (!portalId) return;
      try {
        const state = await getPortalState(portalId);
        socket.emit('portal:state', {
          type: 'single',
          portals: state ? [state] : [],
          timestamp: new Date().toISOString(),
        });
      } catch (err) {
        console.error(`[Socket.IO] Error atendiendo portal:request para ${portalId}:`, err.message);
      }
    });
  });

  // --- Polling de seguridad cada REDIS_SYNC_INTERVAL_MS (default 30s) ---
  // Re-sincroniza estado de portales para capturar cambios que no pasen por Kafka
  // (expiración de transfers TTL, cambios manuales, etc.)
  syncInterval = setInterval(async () => {
    const clientCount = rfid.sockets.size;
    if (clientCount === 0) return; // No hay clientes — no malgastar ciclos

    try {
      const portalStates = await getAllPortalStates();
      if (portalStates.length > 0) {
        rfid.emit('portal:state', {
          type: 'sync',
          portals: portalStates,
          timestamp: new Date().toISOString(),
        });
        console.debug(`[Socket.IO] Sync periódico emitido: ${portalStates.length} portales → ${clientCount} clientes`);
      }
    } catch (err) {
      console.error('[Socket.IO] Error en sync periódico:', err.message);
    }
  }, config.redis.syncIntervalMs);

  console.info(`[Socket.IO] Servidor iniciado en namespace ${config.socket.namespace}`);
  console.info(`[Socket.IO] Sync periódico cada ${config.redis.syncIntervalMs / 1000}s`);

  return io;
}

/**
 * Emite eventos WebSocket basados en el mensaje Kafka clasificado.
 * Llamado desde el consumer Kafka por cada mensaje de rfid.validated.
 *
 * Eventos emitidos:
 *   epc:read    → lectura válida normal
 *   epc:anomaly → lectura con anomalía (explícita o inferida)
 *   portal:state → estado del portal afectado (siempre, para mantener UI actualizada)
 *
 * @param {{ type: string, message: object, anomalyType: string|null, reason: string }} classified
 */
export async function emitFromKafkaMessage(classified) {
  if (!io) {
    console.warn('[Socket.IO] emitFromKafkaMessage llamado antes de inicializar el servidor');
    return;
  }

  const rfid = io.of(config.socket.namespace);
  const { type, message, anomalyType } = classified;

  // Construir payload base compartido entre epc:read y epc:anomaly
  const basePayload = {
    correlationId: message.correlationId,
    epcCode: message.epcCode,
    readerId: message.readerId,
    portalId: message.portalId,
    timestamp: message.timestamp || message._receivedAt,
    transferId: message.transferId ?? null,
    rawReadId: message.rawReadId ?? null,
  };

  if (type === 'normal') {
    rfid.emit('epc:read', {
      ...basePayload,
      status: message.status,
    });
    console.debug(`[Socket.IO] epc:read emitido | epcCode=${message.epcCode} | portalId=${message.portalId}`);
  } else {
    rfid.emit('epc:anomaly', {
      ...basePayload,
      status: message.status,
      anomalyType,
      reason: classified.reason,
    });
    console.warn(`[Socket.IO] epc:anomaly emitido | epcCode=${message.epcCode} | anomalyType=${anomalyType} | portalId=${message.portalId}`);
  }

  // Siempre actualizar el estado del portal afectado desde Redis
  // Esto hace el evento portal:state reactivo a cada lectura Kafka
  if (message.portalId) {
    try {
      const portalState = await getPortalState(message.portalId);
      rfid.emit('portal:state', {
        type: 'update',
        portals: portalState ? [portalState] : [{ portalId: message.portalId, status: 'UNKNOWN' }],
        timestamp: new Date().toISOString(),
        triggeredBy: message.correlationId,
      });
    } catch (err) {
      console.error(`[Socket.IO] Error leyendo estado portal ${message.portalId}:`, err.message);
    }
  }
}

/**
 * Detiene el polling periódico (usado en graceful shutdown).
 */
export function stopSocketServer() {
  if (syncInterval) {
    clearInterval(syncInterval);
    syncInterval = null;
    console.info('[Socket.IO] Polling periódico detenido');
  }
  if (io) {
    io.close();
    io = null;
  }
}
