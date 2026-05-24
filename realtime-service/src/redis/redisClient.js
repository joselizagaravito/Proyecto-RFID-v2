// src/redis/redisClient.js
import Redis from 'ioredis';
import { config } from '../config.js';

let client = null;

/**
 * Crea y devuelve el cliente Redis singleton con reconexión automática.
 * ioredis 5.x maneja reconexión internamente — no necesitamos lógica manual.
 */
export function getRedisClient() {
  if (client) return client;

  client = new Redis({
    host: config.redis.host,
    port: config.redis.port,
    // Reintentos con backoff exponencial: 100ms, 200ms, 400ms... hasta 3s
    retryStrategy(times) {
      const delay = Math.min(100 * 2 ** times, 3000);
      console.warn(`[Redis] Reintento #${times} en ${delay}ms`);
      return delay;
    },
    maxRetriesPerRequest: null, // No lanzar error en comandos durante reconexión
    enableReadyCheck: true,
    lazyConnect: false,
  });

  client.on('connect', () => console.info('[Redis] Conectado'));
  client.on('ready', () => console.info('[Redis] Listo para recibir comandos'));
  client.on('error', (err) => console.error('[Redis] Error:', err.message));
  client.on('reconnecting', (ms) => console.warn(`[Redis] Reconectando en ${ms}ms`));

  return client;
}

/**
 * Lee el estado de un portal específico desde Redis.
 * Clave: portal:state:{portalId}  →  Hash con campos { status, transferId, updatedAt, ... }
 * Devuelve null si la clave no existe.
 */
export async function getPortalState(portalId) {
  const redis = getRedisClient();
  const key = `${config.redis.keyPrefix.portalState}${portalId}`;
  const data = await redis.hgetall(key);
  // hgetall devuelve {} si la clave no existe
  return Object.keys(data).length > 0 ? { portalId, ...data } : null;
}

/**
 * Lee todos los estados de portales.
 * Escanea claves portal:state:* con SCAN para no bloquear Redis.
 * Devuelve array de objetos { portalId, status, transferId, ... }
 */
export async function getAllPortalStates() {
  const redis = getRedisClient();
  const pattern = `${config.redis.keyPrefix.portalState}*`;
  const keys = await scanKeys(redis, pattern);

  if (keys.length === 0) return [];

  // Pipeline para leer todos los hashes de una sola vez
  const pipeline = redis.pipeline();
  for (const key of keys) {
    pipeline.hgetall(key);
  }
  const results = await pipeline.exec();

  return results
    .map(([err, data], idx) => {
      if (err || !data || Object.keys(data).length === 0) return null;
      // Extraer portalId del nombre de la clave
      const portalId = keys[idx].replace(config.redis.keyPrefix.portalState, '');
      return { portalId, ...data };
    })
    .filter(Boolean);
}

/**
 * Lee el transfer activo de un portal.
 * Clave: transfer:active:{portalId}  →  Hash con campos del transfer
 */
export async function getActiveTransfer(portalId) {
  const redis = getRedisClient();
  const key = `${config.redis.keyPrefix.transferActive}${portalId}`;
  const data = await redis.hgetall(key);
  return Object.keys(data).length > 0 ? { portalId, ...data } : null;
}

/**
 * Escanea claves de Redis con SCAN (no bloquea).
 * Devuelve array con todas las claves que coinciden el patrón.
 */
async function scanKeys(redis, pattern) {
  const keys = [];
  let cursor = '0';
  do {
    const [nextCursor, batch] = await redis.scan(cursor, 'MATCH', pattern, 'COUNT', 100);
    cursor = nextCursor;
    keys.push(...batch);
  } while (cursor !== '0');
  return keys;
}
