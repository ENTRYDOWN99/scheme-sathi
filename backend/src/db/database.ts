import fs from 'node:fs';
import path from 'node:path';
import type { DatabaseSync } from 'node:sqlite';
import { Pool } from 'pg';
import { config } from '../config/env.js';

export type SqlParam = string | number | boolean | null;

export interface DatabaseClient {
  readonly dialect: 'sqlite' | 'postgres';
  query<T = Record<string, unknown>>(sql: string, params?: SqlParam[]): Promise<T[]>;
  get<T = Record<string, unknown>>(sql: string, params?: SqlParam[]): Promise<T | null>;
  run(sql: string, params?: SqlParam[]): Promise<{ changes: number }>;
  exec(sql: string): Promise<void>;
  close(): Promise<void>;
}

function normalizeParam(val: SqlParam): string | number | null {
  if (typeof val === 'boolean') {
    return val ? 1 : 0;
  }
  return val;
}

class SqliteAdapter implements DatabaseClient {
  readonly dialect = 'sqlite' as const;
  private dbPromise: Promise<DatabaseSync>;

  constructor(filePath: string) {
    this.dbPromise = this.openDatabase(filePath);
  }

  private async openDatabase(filePath: string): Promise<DatabaseSync> {
    const { DatabaseSync } = await import('node:sqlite');
    let db: DatabaseSync;
    if (filePath !== ':memory:') {
      const resolved = path.resolve(filePath);
      fs.mkdirSync(path.dirname(resolved), { recursive: true });
      db = new DatabaseSync(resolved);
    } else {
      db = new DatabaseSync(':memory:');
    }
    db.exec('PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;');
    return db;
  }

  async query<T = Record<string, unknown>>(sql: string, params: SqlParam[] = []): Promise<T[]> {
    const db = await this.dbPromise;
    const stmt = db.prepare(sql);
    const rows = stmt.all(...params.map(normalizeParam)) as T[];
    return rows;
  }

  async get<T = Record<string, unknown>>(sql: string, params: SqlParam[] = []): Promise<T | null> {
    const db = await this.dbPromise;
    const stmt = db.prepare(sql);
    const row = stmt.get(...params.map(normalizeParam)) as T | undefined;
    return row ?? null;
  }

  async run(sql: string, params: SqlParam[] = []): Promise<{ changes: number }> {
    const db = await this.dbPromise;
    const stmt = db.prepare(sql);
    const result = stmt.run(...params.map(normalizeParam));
    return { changes: Number(result.changes ?? 0) };
  }

  async exec(sql: string): Promise<void> {
    const db = await this.dbPromise;
    db.exec(sql);
  }

  async close(): Promise<void> {
    const db = await this.dbPromise;
    db.close();
  }
}

function convertPlaceholdersToPg(sql: string): string {
  let index = 0;
  return sql.replace(/\?/g, () => `$${++index}`);
}

class PostgresAdapter implements DatabaseClient {
  readonly dialect = 'postgres' as const;
  private pool: Pool;

  constructor(connectionString: string) {
    const isProduction = process.env.NODE_ENV === 'production' || config.nodeEnv === 'production';
    const useSsl = isProduction && !connectionString.includes('sslmode=disable');
    this.pool = new Pool({
      connectionString,
      ...(useSsl ? { ssl: { rejectUnauthorized: false } } : {})
    });
  }

  async query<T = Record<string, unknown>>(sql: string, params: SqlParam[] = []): Promise<T[]> {
    const res = await this.pool.query(convertPlaceholdersToPg(sql), params.map(normalizeParam));
    return res.rows as T[];
  }

  async get<T = Record<string, unknown>>(sql: string, params: SqlParam[] = []): Promise<T | null> {
    const rows = await this.query<T>(sql, params);
    return rows[0] ?? null;
  }

  async run(sql: string, params: SqlParam[] = []): Promise<{ changes: number }> {
    const res = await this.pool.query(convertPlaceholdersToPg(sql), params.map(normalizeParam));
    return { changes: res.rowCount ?? 0 };
  }

  async exec(sql: string): Promise<void> {
    await this.pool.query(sql);
  }

  async close(): Promise<void> {
    await this.pool.end();
  }
}

export function createDatabase(databaseUrl: string = config.databaseUrl): DatabaseClient {
  if (databaseUrl.startsWith('postgres://') || databaseUrl.startsWith('postgresql://')) {
    return new PostgresAdapter(databaseUrl);
  }
  if (databaseUrl === ':memory:' || databaseUrl === 'sqlite::memory:') {
    return new SqliteAdapter(':memory:');
  }
  const filePath = databaseUrl.startsWith('sqlite:')
    ? databaseUrl.slice('sqlite:'.length)
    : databaseUrl;
  return new SqliteAdapter(filePath);
}

let sharedDb: DatabaseClient | null = null;

export function getDb(): DatabaseClient {
  if (!sharedDb) {
    sharedDb = createDatabase();
  }
  return sharedDb;
}

export async function closeDb(): Promise<void> {
  if (sharedDb) {
    await sharedDb.close();
    sharedDb = null;
  }
}
