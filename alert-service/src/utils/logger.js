// src/utils/logger.js
import pino from 'pino';

export const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  base: { service: 'alert-service' },
  timestamp: pino.stdTimeFunctions.isoTime,
});
