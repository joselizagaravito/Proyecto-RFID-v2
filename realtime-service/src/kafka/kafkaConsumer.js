// src/kafka/kafkaConsumer.js
import { Kafka, logLevel } from 'kafkajs';
import { config } from '../config.js';
import { classifyMessage } from './messageClassifier.js';

let consumer = null;
let kafkaInstance = null;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function startKafkaConsumer(onMessage, onSessionMessage) {
  kafkaInstance = new Kafka({
    clientId: 'realtime-service',
    brokers: config.kafka.brokers,
    retry: { initialRetryTime: 3000, retries: 15, factor: 1.5, maxRetryTime: 30000 },
    logLevel: process.env.NODE_ENV === 'development' ? logLevel.INFO : logLevel.WARN,
  });
  await connectWithRetry(onMessage, onSessionMessage);
}

async function connectWithRetry(onMessage, onSessionMessage, attempt = 1) {
  const MAX_ATTEMPTS = 10;
  const BASE_DELAY_MS = 5000;
  try {
    consumer = kafkaInstance.consumer({
      groupId: config.kafka.groupId,
      heartbeatInterval: 3000,
      sessionTimeout: 30000,
      retry: { initialRetryTime: 3000, retries: 10 },
    });

    consumer.on(consumer.events.CRASH, async ({ payload }) => {
      console.error(`[Kafka] Consumer crash: ${payload.error?.message}`);
      console.warn('[Kafka] Reconectando en 10s...');
      await sleep(10000);
      await connectWithRetry(onMessage, onSessionMessage, 1);
    });

    await consumer.connect();
    console.info('[Kafka] Consumidor conectado');
    await consumer.subscribe({ topic: config.kafka.topic.validated, fromBeginning: false });
    await consumer.subscribe({ topic: config.kafka.topic.session, fromBeginning: false });
    console.info(`[Kafka] Suscrito a topic: ${config.kafka.topic.validated}`);

    await consumer.run({
      eachMessage: async ({ topic, partition, message }) => {
        const rawValue = message.value?.toString();
        if (!rawValue) { console.warn('[Kafka] Mensaje vacio'); return; }
        let parsed;
        try { parsed = JSON.parse(rawValue); }
        catch (err) { console.error('[Kafka] JSON invalido:', err.message); return; }
        parsed._kafkaOffset = message.offset;
        parsed._kafkaPartition = partition;
        parsed._receivedAt = new Date().toISOString();

        if (topic === config.kafka.topic.session) {
          try {
            onSessionMessage(parsed);
            console.debug(`[Kafka] session | tipo=${parsed.resultType} | portal=${parsed.portalId}`);
          } catch (err) {
            console.error('[Kafka] Error procesando mensaje de sesion:', err.message);
          }
          return;
        }

        if (parsed.epc && !parsed.epcCode) parsed.epcCode = parsed.epc;
        if (parsed.result && !parsed.status) parsed.status = parsed.result;
        if (parsed.deviceId && !parsed.portalId) parsed.portalId = parsed.deviceId;
        if (!parsed.status) parsed.status = "VALID";
        try {
          const classified = await classifyMessage(parsed);
          console.debug(`[Kafka] '${classified.type}' | epc=${parsed.epcCode} | portal=${parsed.portalId}`);
          onMessage(classified);
        } catch (err) {
          console.error('[Kafka] Error procesando:', err.message);
        }
      },
    });
    console.info('[Kafka] Consumer corriendo y escuchando mensajes');
  } catch (err) {
    const isCoordErr = err.message?.includes('GroupCoordinator') || err.message?.includes('group coordinator');
    if (attempt <= MAX_ATTEMPTS) {
      const delay = Math.min(BASE_DELAY_MS * attempt, 30000);
      console.warn(`[Kafka] ${isCoordErr ? 'GroupCoordinator no disponible' : err.message} — reintento ${attempt}/${MAX_ATTEMPTS} en ${delay/1000}s`);
      try { await consumer?.disconnect(); } catch (_) {}
      consumer = null;
      await sleep(delay);
      return connectWithRetry(onMessage, onSessionMessage, attempt + 1);
    }
    console.error(`[Kafka] No se pudo conectar tras ${MAX_ATTEMPTS} intentos.`);
  }
}

export async function stopKafkaConsumer() {
  if (consumer) {
    await consumer.disconnect();
    console.info('[Kafka] Consumidor desconectado');
    consumer = null;
  }
}
