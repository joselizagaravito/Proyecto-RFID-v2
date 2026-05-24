import pg from "pg";
import { config } from "../config.js";
import { logger } from "../utils/logger.js";

const { Pool } = pg;
let pool = null;

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function toUuid(value) {
  if (!value) return null;
  return UUID_REGEX.test(value) ? value : null;
}

export function getPool() {
  if (!pool) {
    pool = new Pool({
      connectionString: config.db.connectionString,
      ssl: config.db.ssl,
      max: config.db.max,
    });
    pool.on("error", (err) => {
      logger.error({ err }, "[DB] Error inesperado en cliente inactivo");
    });
  }
  return pool;
}

export async function insertAlertLog(alert) {
  const sql = `
    INSERT INTO alert_log (
      id, transfer_id, alert_type, lpn_code, epc, sku_code,
      expected_quantity, received_quantity, device_id, portal_location,
      webhook_url, webhook_status, retry_count, delivered_at, correlation_id, created_at
    ) VALUES (
      $1, $2, $3, $4, $5, $6,
      $7, $8, $9, $10,
      $11, $12, $13, $14, $15, NOW()
    )
    ON CONFLICT (id) DO NOTHING
  `;
  const values = [
    alert.alertId,
    toUuid(alert.transferId),
    alert.alertType,
    alert.lpnCode          || null,
    alert.epc              || null,
    alert.skuCode          || null,
    alert.expectedQuantity || null,
    alert.receivedQuantity || null,
    alert.deviceId         || null,
    alert.portalLocation   || null,
    config.webhook.url,
    alert.webhookStatus    || null,
    alert.retryCount       || 0,
    alert.deliveredAt      || null,
    toUuid(alert.correlationId),
  ];
  await getPool().query(sql, values);
}

export async function updateAlertWebhookResult(alertId, webhookStatus, retryCount, deliveredAt) {
  const sql = `
    UPDATE alert_log
    SET webhook_status = $2,
        retry_count    = $3,
        delivered_at   = $4
    WHERE id = $1
  `;
  await getPool().query(sql, [alertId, webhookStatus, retryCount, deliveredAt || null]);
}

export async function closePool() {
  if (pool) {
    await pool.end();
    pool = null;
  }
}