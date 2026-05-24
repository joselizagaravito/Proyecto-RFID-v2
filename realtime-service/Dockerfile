# ============================================================
# Stage 1: builder — instala dependencias de producción
# ============================================================
FROM node:22-alpine AS builder

WORKDIR /app

# Copiar manifiestos primero para aprovechar caché de capas Docker
COPY package.json ./

# Instalar solo dependencias de producción (sin devDependencies)
RUN npm install --omit=dev --no-audit --no-fund

# ============================================================
# Stage 2: production — imagen final mínima
# ============================================================
FROM node:22-alpine AS production

# Metadatos
LABEL maintainer="pystelectronic"
LABEL service="realtime-service"
LABEL version="1.0.0"

WORKDIR /app

# Usuario no-root para seguridad (node ya viene en la imagen alpine)
RUN chown -R node:node /app
USER node

# Copiar node_modules del builder (sin caché de npm)
COPY --from=builder --chown=node:node /app/node_modules ./node_modules

# Copiar código fuente
COPY --chown=node:node src/ ./src/
COPY --chown=node:node package.json ./

# Puerto de Socket.IO + HTTP health check
EXPOSE 3001

# Health check para Docker Compose
# Espera 20s antes del primer check — da tiempo a que Kafka/Redis estén listos
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:3001/health || exit 1

# Comando de inicio — Node.js 22 soporta ESModules nativamente sin flags extra
CMD ["node", "src/index.js"]
