FROM node:22-alpine AS builder
WORKDIR /repo

COPY shared ./shared
COPY web/package*.json ./web/
WORKDIR /repo/web
RUN npm ci

COPY web ./
ARG NEXT_PUBLIC_API_URL=http://localhost:4000/api/v1
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000

COPY --from=builder /repo/web/.next/standalone ./
COPY --from=builder /repo/web/.next/static ./.next/static
COPY --from=builder /repo/web/public ./public

EXPOSE 3000
CMD ["node", "server.js"]

