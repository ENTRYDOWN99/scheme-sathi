import { createApp } from './app.js';
import { config } from './config/env.js';
import { closeDb, getDb } from './db/database.js';
import { runMigrations } from './db/migrate.js';
import { seedDatabase } from './db/seed.js';

async function bootstrap() {
  const db = getDb();
  await runMigrations(db);
  await seedDatabase(db);

  const app = createApp(db);
  const server = app.listen(config.port, '0.0.0.0', () => {
    console.info(
      `[server] SchemeSaathi API listening on http://0.0.0.0:${config.port} (docs: /api/docs, dialect: ${db.dialect})`
    );
  });

  const shutdown = async (signal: string) => {
    console.info(`[server] received ${signal}, closing...`);
    server.close(async () => {
      await closeDb();
      process.exit(0);
    });
  };

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
}

bootstrap().catch(async (err) => {
  console.error('[server] fatal startup failure:', err);
  await closeDb();
  process.exit(1);
});
