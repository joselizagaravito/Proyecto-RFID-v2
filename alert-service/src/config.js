// src/config.js
// Variables de entorno con valores por defecto para desarrollo local

export const config = {
  // ── Kafka ──────────────────────────────────────────────
  kafka: {
    brokers: (process.env.KAFKA_BOOTSTRAP || 'kafka:9092').split(','),
    groupId: 'alert-service-group',
    topics: {
      alerts:         'transfer.alerts',   // producido por validation-service
      transferEvents: 'transfer.events',   // producido por validation-service (PALLET_INCOMPLETE / MISSING_LPN)
    },
    retry: {
      initialRetryTime: 3000,
      retries: 10,
    },
  },

  // ── PostgreSQL ─────────────────────────────────────────
  db: {
    connectionString: process.env.DB_URL ||
      'postgresql://rfid_app:rfid_pass@postgres:5432/rfid_transfers',
    ssl: process.env.DB_SSL === 'true' ? { rejectUnauthorized: false } : false,
    max: 5,
  },

  // ── Webhook WMS/ERP ────────────────────────────────────
  webhook: {
    url:        process.env.WEBHOOK_URL        || 'http://webhook-mock:3003/webhook',
    hmacSecret: process.env.WEBHOOK_HMAC_SECRET || 'rfid-hmac-secret-dev',
    timeoutMs:  parseInt(process.env.WEBHOOK_TIMEOUT_MS  || '5000', 10),
    maxRetries: parseInt(process.env.WEBHOOK_MAX_RETRIES || '3', 10),
    // Backoff exponencial: 1s, 3s, 9s
    backoffMs:  [1000, 3000, 9000],
  },

  // ── HTTP (health check) ────────────────────────────────
  http: {
    port: parseInt(process.env.PORT || '3002', 10),
  },

  // ── Tipos de anomalía reconocidos ─────────────────────
  anomalyTypes: new Set([
    'PALLET_INCOMPLETE',
    'EXTRA_LPN',
    'UNREGISTERED_EPC',
    'MISSING_LPN',
    'QUANTITY_MISMATCH',
  ]),
};
