FROM node:22-alpine

LABEL service="webhook-mock"

WORKDIR /app

RUN chown -R node:node /app
USER node

COPY --chown=node:node package.json ./
RUN npm install --omit=dev

COPY --chown=node:node src/ ./src/

EXPOSE 3003

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:3003/health || exit 1

CMD ["node", "src/index.js"]
