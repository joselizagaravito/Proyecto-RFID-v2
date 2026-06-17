// src/config.js
// Todas las variables de entorno del servicio — con valores por defecto para desarrollo local

export const config = {
  // Kafka
  kafka: {
    brokers: (process.env.KAFKA_BOOTSTRAP || 'kafka:9092').split(','),
    groupId: 'realtime-service-group',
    topic: {
      validated: 'rfid.validated',
      session:   'rfid.session',
    },
    // Reintentos con backoff exponencial para reconexión robusta
    retry: {
      initialRetryTime: 3000,
      retries: 10,
    },
  },

  // Redis
  redis: {
    host: process.env.REDIS_HOST || 'redis',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    // Prefijos de claves Redis — deben coincidir con los de validation-service
    keyPrefix: {
      portalState: 'portal:state:',
      transferActive: 'transfer:active:',
    },
    // Intervalo de polling de seguridad para re-sincronizar estado (ms)
    syncIntervalMs: parseInt(process.env.REDIS_SYNC_INTERVAL_MS || '30000', 10),
  },

  // Socket.IO
  socket: {
    port: parseInt(process.env.PORT || '3001', 10),
    namespace: '/rfid',
    cors: {
      // En producción restringir al dominio del frontend
      origin: process.env.CORS_ORIGIN || '*',
      methods: ['GET', 'POST'],
    },
  },

  // Clasificación de anomalías — tipos que disparan evento epc:anomaly
  // Si el campo anomalyType es null pero status === 'ANOMALY' también se considera anomalía
  anomalyStatuses: new Set(['ANOMALY', 'DUPLICATE', 'UNKNOWN_EPC']),
  anomalyTypes: new Set([
    'UNREGISTERED_EPC',
    'NO_ACTIVE_TRANSFER',
    'DUPLICATE_READ',
    'WRONG_PORTAL',
  ]),
};
