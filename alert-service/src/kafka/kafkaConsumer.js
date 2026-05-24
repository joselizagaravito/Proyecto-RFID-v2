// src/kafka/kafkaConsumer.js
import { Kafka, logLevel } from 'kafkajs';
import { config } from '../config.js';
import { logger } from '../utils/logger.js';
import { processTransferAlert, processTransferEvent } from './alertProcessor.js';

let kafka  = null;
let consumer = null;

export async function startKafkaConsumer() {
  kafka = new Kafka({
    clientId: 'alert-service',
    brokers:   config.kafka.brokers,
    retry:     config.kafka.retry,
    logLevel:  logLevel.WARN,
    // Suprime logs de KafkaJS para usar pino
    logCreator: () => ({ namespace, level, label, log }) => {
      const { message, ...extra } = log;
      if (level === logLevel.ERROR) logger.error({ ...extra }, `[KafkaJS] ${message}`);
      else if (level === logLevel.WARN)  logger.warn({ ...extra }, `[KafkaJS] ${message}`);
    },
  });

  consumer = kafka.consumer({
    groupId:       config.kafka.groupId,
    sessionTimeout: 30000,
    heartbeatInterval: 3000,
    maxWaitTimeInMs:   5000,
  });

  // Reconexión robusta
  consumer.on(consumer.events.CRASH, async ({ payload }) => {
    logger.error({ error: payload.error.message }, '[Kafka] Consumer crash — reconectando en 5s');
    await new Promise(r => setTimeout(r, 5000));
    await startKafkaConsumer();
  });

  await consumer.connect();
  logger.info('[Kafka] Conectado al broker');

  await consumer.subscribe({
    topics: [
      config.kafka.topics.alerts,
      config.kafka.topics.transferEvents,
    ],
    fromBeginning: false,
  });

  logger.info({
    topics: [config.kafka.topics.alerts, config.kafka.topics.transferEvents],
  }, '[Kafka] Suscrito a topics');

  await consumer.run({
    autoCommit: true,
    eachMessage: async ({ topic, partition, message }) => {
      const rawValue = message.value?.toString();
      if (!rawValue) return;

      try {
        if (topic === config.kafka.topics.alerts) {
          await processTransferAlert(rawValue);
        } else if (topic === config.kafka.topics.transferEvents) {
          await processTransferEvent(rawValue);
        }
      } catch (err) {
        logger.error({
          err,
          topic,
          partition,
          offset: message.offset,
        }, '[Kafka] Error procesando mensaje — se continúa (no bloquea el consumer)');
        // No lanzamos el error para no crashear el consumer
      }
    },
  });
}

export async function stopKafkaConsumer() {
  if (consumer) {
    await consumer.disconnect();
    consumer = null;
    logger.info('[Kafka] Consumer desconectado');
  }
}
