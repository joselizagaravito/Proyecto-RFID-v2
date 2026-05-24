// src/index.js
// realtime-service — Sprint 5 T2
// Node.js 22 + KafkaJS 2.2 + ioredis 5.4 + Socket.IO 4.7
import http from 'http';
import express from 'express';
import { config } from './config.js';
import { initSocketServer, emitFromKafkaMessage, stopSocketServer } from './socket/socketServer.js';
import { startKafkaConsumer, stopKafkaConsumer } from './kafka/kafkaConsumer.js';
import { getRedisClient } from './redis/redisClient.js';
import healthRouter from './health/healthRouter.js';

async function main() {
  console.info('=== realtime-service iniciando ===');
  console.info(`Kafka brokers: ${config.kafka.brokers.join(', ')}`);
  console.info(`Redis: ${config.redis.host}:${config.redis.port}`);
  console.info(`Puerto WebSocket: ${config.socket.port}`);

  // 1. Crear app Express (solo para /health)
  const app = express();
  app.use(express.json());
  app.use('/', healthRouter);

  // 2. Crear servidor HTTP (Express + Socket.IO comparten el mismo puerto)
  const httpServer = http.createServer(app);

  // 3. Inicializar Socket.IO sobre el servidor HTTP
  initSocketServer(httpServer);

  // 4. Inicializar cliente Redis (conexión lazy — falla suave si no está disponible al inicio)
  getRedisClient();

  // 5. Iniciar servidor HTTP en el puerto configurado
  await new Promise((resolve) => {
    httpServer.listen(config.socket.port, () => {
      console.info(`[HTTP] Servidor escuchando en puerto ${config.socket.port}`);
      console.info(`[Socket.IO] Namespace activo: ${config.socket.namespace}`);
      resolve();
    });
  });

  // 6. Iniciar consumidor Kafka — callback conecta mensajes con Socket.IO
  await startKafkaConsumer(emitFromKafkaMessage);

  console.info('=== realtime-service listo ===');
}

// --- Graceful shutdown ---
// Capturamos SIGTERM (Docker stop) y SIGINT (Ctrl+C en dev)
async function shutdown(signal) {
  console.info(`\n[Shutdown] Señal recibida: ${signal}`);
  try {
    await stopKafkaConsumer();
    stopSocketServer();
    const redis = getRedisClient();
    await redis.quit();
    console.info('[Shutdown] Completado limpiamente');
    process.exit(0);
  } catch (err) {
    console.error('[Shutdown] Error durante cierre:', err.message);
    process.exit(1);
  }
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

// Capturar excepciones no manejadas para evitar crash silencioso
process.on('uncaughtException', (err) => {
  console.error('[FATAL] Excepción no capturada:', err);
  shutdown('uncaughtException');
});

process.on('unhandledRejection', (reason) => {
  console.error('[FATAL] Promise rechazada sin manejar:', reason);
  shutdown('unhandledRejection');
});

main().catch((err) => {
  console.error('[FATAL] Error en inicialización:', err);
  process.exit(1);
});
