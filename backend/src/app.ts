import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import cookieParser from 'cookie-parser';
import cors from 'cors';
import express, { type Express } from 'express';
import helmet from 'helmet';
import yaml from 'js-yaml';
import swaggerUi from 'swagger-ui-express';
import { config } from './config/env.js';
import type { DatabaseClient } from './db/database.js';
import { errorHandler, noAadhaarMiddleware, notFoundHandler } from './middleware/errorHandler.js';
import { generalRateLimiter } from './middleware/rateLimiter.js';
import { requestLogger } from './middleware/requestLogger.js';
import { createAiRouter } from './routes/ai.js';
import { createApplicationsRouter } from './routes/applications.js';
import { createAuditRouter } from './routes/audit.js';
import { createAuthRouter } from './routes/auth.js';
import { createChecklistsRouter } from './routes/checklists.js';
import { createProfileRouter } from './routes/profile.js';
import { createSchemesRouter } from './routes/schemes.js';
import { createTasksRouter } from './routes/tasks.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function loadOpenApiSpec(): Record<string, unknown> {
  const candidates = [
    path.resolve(__dirname, '../openapi.yaml'),
    path.resolve(__dirname, '../../openapi.yaml')
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return yaml.load(fs.readFileSync(candidate, 'utf8')) as Record<string, unknown>;
    }
  }
  return { openapi: '3.0.3', info: { title: 'SchemeSaathi API', version: '1.0.0' }, paths: {} };
}

export function createApp(db: DatabaseClient): Express {
  const app = express();

  app.set('trust proxy', 1);
  app.use(
    helmet({
      contentSecurityPolicy: false
    })
  );
  app.use(
    cors({
      origin: (origin, callback) => {
        if (!origin || config.corsOrigins.includes(origin) || config.corsOrigins.includes('*')) {
          callback(null, true);
          return;
        }
        callback(new Error(`Origin ${origin} not allowed by CORS`));
      },
      credentials: true
    })
  );
  app.use(express.json({ limit: '1mb' }));
  app.use(cookieParser());
  app.use(generalRateLimiter);
  app.use(requestLogger);
  app.use(noAadhaarMiddleware);

  const openApiDoc = loadOpenApiSpec();
  app.get('/api/openapi.json', (_req, res) => {
    res.json(openApiDoc);
  });
  app.use('/api/docs', swaggerUi.serve, swaggerUi.setup(openApiDoc));

  app.get('/health', async (_req, res, next) => {
    try {
      await db.get('SELECT 1 as ok');
      res.json({
        status: 'ok',
        service: 'schemesaathi-backend',
        dialect: db.dialect,
        timestamp: new Date().toISOString()
      });
    } catch (err) {
      next(err);
    }
  });

  app.use('/api/v1/auth', createAuthRouter(db));
  app.use('/api/v1/schemes', createSchemesRouter(db));
  app.use('/api/v1/profile', createProfileRouter(db));
  app.use('/api/v1/checklists', createChecklistsRouter(db));
  app.use('/api/v1/applications', createApplicationsRouter(db));
  app.use('/api/v1/tasks', createTasksRouter(db));
  app.use('/api/v1/ai', createAiRouter(db));
  app.use('/api/v1/audit', createAuditRouter(db));

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
