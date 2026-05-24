// src/index.js
// alert-service — punto de entrada principal
// Inicia: servidor Express (health), consumidor Kafka, cliente PostgreSQL

import express from 'express';
import { config } from './config.js';
import { logger } from './utils/logger.js';
import { getPool, closePool } from './db/dbClient.js';
import { startKafkaConsumer, stopKafkaConsumer } from './kafka/kafkaConsumer.js';

// ── Estado global del servicio ─────────────────────────────────────────────────
const state = {
  dbReady:    false,
  kafkaReady: false,
  alertsSent: 0,
  startedAt:  new Date().toISOString(),
};

// ── Servidor Express (health check) ──────────────────────────────────────────
const app = express();
app.use(express.json());

app.get('/health', async (_req, res) => {
  let dbOk = false;
  try {
    await getPool().query('SELECT 1');
    dbOk = true;
  } catch (_) {}

  const status = state.kafkaReady && dbOk ? 'UP' : 'DEGRADED';
  res.status(status === 'UP' ? 200 : 503).json({
    status,
    service:    'alert-service',
    timestamp:  new Date().toISOString(),
    uptime:     process.uptime(),
    checks: {
      kafka:    state.kafkaReady ? 'ok' : 'not ready',
      postgres: dbOk            ? 'ok' : 'error',
    },
    stats: {
      alertsSent: state.alertsSent,
      startedAt:  state.startedAt,
    },
  });
});

// Endpoint de métricas básico (útil para debug)
app.get('/metrics', (_req, res) => {
  res.json({
    alertsSent:    state.alertsSent,
    uptimeSeconds: Math.floor(process.uptime()),
    startedAt:     state.startedAt,
    webhook: {
      url:        config.webhook.url,
      maxRetries: config.webhook.maxRetries,
    },
    kafka: {
      brokers: config.kafka.brokers,
      groupId: config.kafka.groupId,
      topics:  config.kafka.topics,
    },
  });
});

// ── Arranque principal ────────────────────────────────────────────────────────
async function main() {
  logger.info({ port: config.http.port }, '=== alert-service arrancando ===');

  // 1. Verificar conexión a PostgreSQL
  try {
    await getPool().query('SELECT NOW()');
    state.dbReady = true;
    logger.info('[DB] PostgreSQL conectado');
  } catch (err) {
    logger.error({ err }, '[DB] No se pudo conectar a PostgreSQL — continuando sin DB');
  }

  // 2. Iniciar servidor HTTP
  app.listen(config.http.port, () => {
    logger.info({ port: config.http.port }, '[HTTP] Health check disponible en /health');
  });

  // 3. Iniciar consumidor Kafka (con reconexión automática)
  try {
    await startKafkaConsumer();
    state.kafkaReady = true;
    logger.info('[Kafka] Consumer activo');
  } catch (err) {
    logger.error({ err }, '[Kafka] Error al iniciar consumer — el servicio continuará y reintentará');
  }

  logger.info('=== alert-service listo ===');
}

// ── Shutdown graceful ─────────────────────────────────────────────────────────
async function shutdown(signal) {
  logger.info({ signal }, 'Shutdown señal recibida — cerrando alert-service');
  try {
    await stopKafkaConsumer();
    await closePool();
  } catch (err) {
    logger.error({ err }, 'Error durante shutdown');
  }
  process.exit(0);
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT',  () => shutdown('SIGINT'));

process.on('unhandledRejection', (reason) => {
  logger.error({ reason }, '[alert-service] unhandledRejection');
});

main();
