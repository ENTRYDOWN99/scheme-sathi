FROM node:24-alpine AS builder
WORKDIR /app/backend

COPY backend/package*.json ./
RUN npm ci --include=dev

COPY backend/tsconfig.json ./
COPY backend/scripts ./scripts
COPY backend/openapi.yaml ./
COPY backend/src ./src
RUN npm run build

FROM node:24-alpine AS runner
WORKDIR /app/backend
ENV NODE_ENV=production
ENV PORT=4000

COPY backend/package*.json ./
RUN npm ci --omit=dev

COPY --from=builder /app/backend/dist ./dist
COPY --from=builder /app/backend/openapi.yaml ./openapi.yaml

RUN mkdir -p /app/backend/data && chown -R node:node /app/backend
USER node

EXPOSE 4000
CMD ["sh", "-c", "node dist/db/migrate.js && node dist/db/seed.js && node dist/index.js"]

