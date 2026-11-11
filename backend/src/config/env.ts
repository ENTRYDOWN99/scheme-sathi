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
    if (!jwtSecret || jwtSecret.length < 32) {
      throw new Error(
        `Invalid JWT_SECRET: must be set and at least 32 characters in production (got ${jwtSecret.length} characters)`
      );
    }
    if (!jwtRefreshSecret || jwtRefreshSecret.length < 32) {
      throw new Error(
        `Invalid JWT_REFRESH_SECRET: must be set and at least 32 characters in production (got ${jwtRefreshSecret.length} characters)`
      );
    }
    if (!piiEncryptionKey) {
      throw new Error(
        'Invalid PII_ENCRYPTION_KEY: must be set to an exact 64-character hexadecimal string in production'
      );
    }
    if (!/^[0-9a-fA-F]{64}$/.test(piiEncryptionKey)) {
      throw new Error(
        `Invalid PII_ENCRYPTION_KEY: must be an exact 64-character hexadecimal string in production (got length ${piiEncryptionKey.length})`
      );
    }
  }

  const corsOriginEnv = process.env.CORS_ORIGIN;
  if (nodeEnv === 'production' && (!corsOriginEnv || !corsOriginEnv.trim())) {
    throw new Error(
      'Invalid CORS_ORIGIN: must be set to one or more comma-separated http(s) URLs in production'
    );
  }

  const corsOriginRaw = corsOriginEnv || 'http://localhost:3000';
  const corsOrigins = corsOriginRaw
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  if (nodeEnv === 'production') {
    for (const origin of corsOrigins) {
      let parsed: URL;
      try {
        parsed = new URL(origin);
      } catch {
        throw new Error(
          `Invalid CORS_ORIGIN: "${origin}" is not a valid URL (expected http:// or https:// origin)`
        );
      }
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        throw new Error(`Invalid CORS_ORIGIN: "${origin}" must use http: or https: protocol`);
      }
    }
  }

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
