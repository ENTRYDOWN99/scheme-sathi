import dotenv from 'dotenv';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.resolve(__dirname, '../../../.env') });
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

export interface AppConfig {
  port: number;
  nodeEnv: 'development' | 'test' | 'production';
  logLevel: string;
  corsOrigins: string[];
  databaseUrl: string;
  jwtSecret: string;
  jwtRefreshSecret: string;
  jwtAccessExpiresIn: string;
  jwtRefreshExpiresIn: string;
  piiEncryptionKey: string;
  geminiApiKey: string;
  geminiModel: string;
  geminiFallbackModel: string;
  geminiTimeoutMs: number;
  cookieSecure: boolean;
}

const DEFAULT_DEV_PII_KEY = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';

export function loadConfig(): AppConfig {
  const nodeEnv = (process.env.NODE_ENV as AppConfig['nodeEnv']) || 'development';
  const jwtSecret =
    process.env.JWT_SECRET ||
    (nodeEnv === 'production' ? '' : 'dev-only-change-me-jwt-access-secret-key-32-chars');
  const jwtRefreshSecret =
    process.env.JWT_REFRESH_SECRET ||
    (nodeEnv === 'production' ? '' : 'dev-only-change-me-jwt-refresh-secret-key-32-chars');
  const piiEncryptionKey =
    process.env.PII_ENCRYPTION_KEY || (nodeEnv === 'production' ? '' : DEFAULT_DEV_PII_KEY);

  if (nodeEnv === 'production') {
    if (jwtSecret.length < 32 || jwtRefreshSecret.length < 32) {
      throw new Error('JWT_SECRET and JWT_REFRESH_SECRET must be >= 32 characters in production');
    }
    if (!/^[0-9a-fA-F]{64}$/.test(piiEncryptionKey)) {
      throw new Error('PII_ENCRYPTION_KEY must be a 64-character hex string in production');
    }
  }

  const corsOriginRaw = process.env.CORS_ORIGIN || 'http://localhost:3000';
  const corsOrigins = corsOriginRaw
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  return {
    port: Number(process.env.PORT || 4000),
    nodeEnv,
    logLevel: process.env.LOG_LEVEL || 'info',
    corsOrigins,
    databaseUrl: process.env.DATABASE_URL || 'sqlite:./data/saathi.db',
    jwtSecret,
    jwtRefreshSecret,
    jwtAccessExpiresIn: process.env.JWT_ACCESS_EXPIRES_IN || '15m',
    jwtRefreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d',
    piiEncryptionKey,
    geminiApiKey: process.env.GEMINI_API_KEY || '',
    geminiModel: process.env.GEMINI_MODEL || 'gemini-2.5-flash',
    geminiFallbackModel: process.env.GEMINI_FALLBACK_MODEL || 'gemini-2.0-flash-lite',
    geminiTimeoutMs: Number(process.env.GEMINI_TIMEOUT_MS || 15000),
    cookieSecure: nodeEnv === 'production'
  };
}

export const config = loadConfig();
