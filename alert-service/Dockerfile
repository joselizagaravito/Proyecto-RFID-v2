# ============================================================
# Stage 1: builder
# ============================================================
FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev

# ============================================================
# Stage 2: production
# ============================================================
FROM node:22-alpine AS production

LABEL maintainer="pystelectronic"
LABEL service="alert-service"
LABEL version="1.0.0"

WORKDIR /app

RUN chown -R node:node /app
USER node

COPY --from=builder --chown=node:node /app/node_modules ./node_modules
COPY --chown=node:node src/ ./src/
COPY --chown=node:node package.json ./

EXPOSE 3002

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=3 \
  CMD wget -qO- http://localhost:3002/health || exit 1

CMD ["node", "src/index.js"]
