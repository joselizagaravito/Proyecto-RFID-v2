// src/health/healthRouter.js
import { Router } from 'express';
import { getRedisClient } from '../redis/redisClient.js';

const router = Router();

/**
 * GET /health
 * Verifica conectividad con Redis (Kafka se monitorea por logs).
 * Responde 200 si todo OK, 503 si Redis no responde.
 */
router.get('/health', async (req, res) => {
  const status = {
    service: 'realtime-service',
    timestamp: new Date().toISOString(),
    redis: 'unknown',
  };

  try {
    const redis = getRedisClient();
    await redis.ping();
    status.redis = 'ok';
    res.status(200).json({ status: 'UP', checks: status });
  } catch (err) {
    status.redis = `error: ${err.message}`;
    res.status(503).json({ status: 'DOWN', checks: status });
  }
});

export default router;
