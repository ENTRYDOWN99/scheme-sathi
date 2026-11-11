import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createDatabase, type DatabaseClient } from './database.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export async function runMigrations(db: DatabaseClient): Promise<string[]> {
  const migrationsDir = path.join(__dirname, 'migrations');
  const files = fs
    .readdirSync(migrationsDir)
    .filter((f) => f.endsWith('.sql'))
    .sort();

  const applied: string[] = [];

  // Ensure schema_migrations exists first
  await db.exec(
    'CREATE TABLE IF NOT EXISTS schema_migrations (id TEXT PRIMARY KEY, applied_at TEXT NOT NULL);'
  );

  for (const file of files) {
    const existing = await db.get<{ id: string }>('SELECT id FROM schema_migrations WHERE id = ?', [
      file
    ]);
    if (existing) {
      continue;
    }

    const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf8');
    await db.exec(sql);
    await db.run(
      'INSERT INTO schema_migrations (id, applied_at) VALUES (?, ?) ON CONFLICT (id) DO NOTHING',
      [file, new Date().toISOString()]
    );
    applied.push(file);
  }

  return applied;
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  const db = createDatabase();
  runMigrations(db)
    .then(async (applied) => {
      console.info(
        '[migrate] applied:',
        applied.length > 0 ? applied.join(', ') : 'none (up to date)'
      );
      await db.close();
    })
    .catch(async (err) => {
      console.error('[migrate] failed:', err);
      await db.close();
      process.exit(1);
    });
}
