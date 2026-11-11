import crypto from 'node:crypto';
import { Router, type Response } from 'express';
import { z } from 'zod';
import { config } from '../config/env.js';
import type { DatabaseClient } from '../db/database.js';
import {
  authenticateRequest,
  generateRefreshTokenPair,
  hashOpaqueToken,
  issueAccessToken,
  type AuthenticatedRequest
} from '../middleware/auth.js';
import { authRateLimiter } from '../middleware/rateLimiter.js';
import { AppError } from '../middleware/errorHandler.js';
import { hashPassword, verifyPassword } from '../security/crypto.js';
import { recordAuditLog } from '../services/auditService.js';
import type { AuthUser, LanguageCode, Role } from '../types/models.js';

interface UserRow {
  id: string;
  email: string | null;
  phone: string | null;
  display_name: string;
  password_hash: string;
  role: Role;
  preferred_language: LanguageCode;
  is_active: number;
}

const registerSchema = z.object({
  email: z.string().email().optional(),
  phone: z.string().min(8).max(16).optional(),
  displayName: z.string().min(2).max(80),
  password: z.string().min(8).max(100),
  preferredLanguage: z.enum(['en', 'hi']).optional()
});

const loginSchema = z.object({
  identifier: z.string().min(3),
  password: z.string().min(1)
});

const demoLoginSchema = z.object({
  role: z.enum(['CITIZEN', 'CSC_OPERATOR', 'SUPERVISOR', 'REVIEWER'])
});

const otpRequestSchema = z.object({
  phoneOrEmail: z.string().min(4)
});

const otpVerifySchema = z.object({
  phoneOrEmail: z.string().min(4),
  code: z.string().min(4).max(8)
});

const refreshSchema = z.object({
  refreshToken: z.string().optional()
});

function rowToAuthUser(row: UserRow): AuthUser {
  return {
    id: row.id,
    email: row.email ?? '',
    phone: row.phone ?? '',
    displayName: row.display_name,
    role: row.role,
    preferredLanguage: row.preferred_language === 'en' ? 'en' : 'hi'
  };
}

function setAuthCookies(res: Response, accessToken: string, refreshToken: string): void {
  const isProd = config.nodeEnv === 'production';
  res.cookie('saathi_access_token', accessToken, {
    httpOnly: true,
    sameSite: 'lax',
    secure: isProd,
    maxAge: 15 * 60 * 1000,
    path: '/'
  });
  res.cookie('saathi_refresh_token', refreshToken, {
    httpOnly: true,
    sameSite: 'lax',
    secure: isProd,
    maxAge: 7 * 24 * 60 * 60 * 1000,
    path: '/'
  });
}

async function createSessionTokens(db: DatabaseClient, user: AuthUser) {
  const accessToken = issueAccessToken(user);
  const { raw: refreshToken, hash: tokenHash } = generateRefreshTokenPair();
  const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString();
  await db.run(
    `INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked_at, created_at)
     VALUES (?, ?, ?, ?, NULL, ?)`,
    [`rt_${crypto.randomUUID()}`, user.id, tokenHash, expiresAt, new Date().toISOString()]
  );
  return { accessToken, refreshToken };
}

export function createAuthRouter(db: DatabaseClient): Router {
  const router = Router();

  router.post('/register', authRateLimiter, async (req, res, next) => {
    try {
      const parsed = registerSchema.parse(req.body);
      if (!parsed.email && !parsed.phone) {
        throw new AppError(400, 'VALIDATION_ERROR', 'Provide either an email or phone number.');
      }

      const existing = await db.get<UserRow>(
        'SELECT * FROM users WHERE (email = ? AND email IS NOT NULL) OR (phone = ? AND phone IS NOT NULL)',
        [parsed.email ?? null, parsed.phone ?? null]
      );
      if (existing) {
        throw new AppError(409, 'USER_EXISTS', 'An account with this email or phone already exists.');
      }

      const now = new Date().toISOString();
      const userId = `usr_${crypto.randomUUID()}`;
      const user: AuthUser = {
        id: userId,
        email: parsed.email ?? '',
        phone: parsed.phone ?? '',
        displayName: parsed.displayName,
        // Self-registration is always CITIZEN; privileged roles require Supervisor provisioning
        role: 'CITIZEN',
        preferredLanguage: parsed.preferredLanguage ?? 'hi'
      };

      await db.run(
        `INSERT INTO users (id, email, phone, display_name, password_hash, role, preferred_language, is_active, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)`,
        [
          user.id,
          parsed.email ?? null,
          parsed.phone ?? null,
          user.displayName,
          hashPassword(parsed.password),
          user.role,
          user.preferredLanguage,
          now,
          now
        ]
      );

      const { accessToken, refreshToken } = await createSessionTokens(db, user);
      setAuthCookies(res, accessToken, refreshToken);
      await recordAuditLog(db, {
        actorId: user.id,
        actorRole: user.role,
        action: 'AUTH_REGISTER',
        entityType: 'USER',
        entityId: user.id,
        details: 'New citizen account registered'
      });

      res.status(201).json({ user, accessToken, refreshToken });
    } catch (err) {
      next(err);
    }
  });

  router.post('/login', authRateLimiter, async (req, res, next) => {
    try {
      const parsed = loginSchema.parse(req.body);
      const userRow = await db.get<UserRow>(
        'SELECT * FROM users WHERE email = ? OR phone = ?',
        [parsed.identifier, parsed.identifier]
      );
      if (!userRow || !userRow.is_active || !verifyPassword(parsed.password, userRow.password_hash)) {
        throw new AppError(401, 'INVALID_CREDENTIALS', 'Invalid email/phone or password.');
      }

      const user = rowToAuthUser(userRow);
      const { accessToken, refreshToken } = await createSessionTokens(db, user);
      setAuthCookies(res, accessToken, refreshToken);
      await recordAuditLog(db, {
        actorId: user.id,
        actorRole: user.role,
        action: 'AUTH_LOGIN',
        entityType: 'USER',
        entityId: user.id,
        details: 'Password login succeeded'
      });

      res.json({ user, accessToken, refreshToken });
    } catch (err) {
      next(err);
    }
  });

  router.post('/demo-login', authRateLimiter, async (req, res, next) => {
    try {
      const parsed = demoLoginSchema.parse(req.body);
      const userRow = await db.get<UserRow>(
        'SELECT * FROM users WHERE role = ? AND is_active = 1 LIMIT 1',
        [parsed.role]
      );
      if (!userRow) {
        throw new AppError(404, 'DEMO_USER_NOT_FOUND', `No demo account seeded for role ${parsed.role}`);
      }
      const user = rowToAuthUser(userRow);
      const { accessToken, refreshToken } = await createSessionTokens(db, user);
      setAuthCookies(res, accessToken, refreshToken);
      await recordAuditLog(db, {
        actorId: user.id,
        actorRole: user.role,
        action: 'AUTH_DEMO_LOGIN',
        entityType: 'USER',
        entityId: user.id,
        details: `Demo session started for role ${user.role}`
      });

      res.json({ user, accessToken, refreshToken });
    } catch (err) {
      next(err);
    }
  });

  router.post('/otp/request', authRateLimiter, async (req, res, next) => {
    try {
      const parsed = otpRequestSchema.parse(req.body);
      const code = String(crypto.randomInt(100000, 999999));
      const expiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString();
      await db.run(
        `INSERT INTO otp_codes (id, phone_or_email, code_hash, expires_at, consumed_at, created_at)
         VALUES (?, ?, ?, ?, NULL, ?)`,
        [
          `otp_${crypto.randomUUID()}`,
          parsed.phoneOrEmail.trim(),
          hashOpaqueToken(code),
          expiresAt,
          new Date().toISOString()
        ]
      );
      // In non-production dev/test mode we return devOtpHint for automated E2E verification; prod uses SMS gateway.
      const devHint = config.nodeEnv !== 'production' ? code : undefined;
      res.json({
        sent: true,
        expiresInSeconds: 300,
        ...(devHint ? { devOtpHint: devHint } : {})
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/otp/verify', authRateLimiter, async (req, res, next) => {
    try {
      const parsed = otpVerifySchema.parse(req.body);
      const otpRow = await db.get<{
        id: string;
        code_hash: string;
        expires_at: string;
        consumed_at: string | null;
      }>(
        `SELECT * FROM otp_codes
         WHERE phone_or_email = ? AND consumed_at IS NULL
         ORDER BY created_at DESC LIMIT 1`,
        [parsed.phoneOrEmail.trim()]
      );

      if (
        !otpRow ||
        otpRow.consumed_at !== null ||
        new Date(otpRow.expires_at).getTime() < Date.now() ||
        otpRow.code_hash !== hashOpaqueToken(parsed.code.trim())
      ) {
        throw new AppError(401, 'INVALID_OTP', 'OTP code is invalid or expired.');
      }

      await db.run('UPDATE otp_codes SET consumed_at = ? WHERE id = ?', [
        new Date().toISOString(),
        otpRow.id
      ]);

      let userRow = await db.get<UserRow>(
        'SELECT * FROM users WHERE email = ? OR phone = ?',
        [parsed.phoneOrEmail.trim(), parsed.phoneOrEmail.trim()]
      );

      if (!userRow) {
        const now = new Date().toISOString();
        const isEmail = parsed.phoneOrEmail.includes('@');
        const newId = `usr_${crypto.randomUUID()}`;
        await db.run(
          `INSERT INTO users (id, email, phone, display_name, password_hash, role, preferred_language, is_active, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, 'CITIZEN', 'hi', 1, ?, ?)`,
          [
            newId,
            isEmail ? parsed.phoneOrEmail.trim() : null,
            isEmail ? null : parsed.phoneOrEmail.trim(),
            'Citizen User',
            hashPassword(crypto.randomBytes(24).toString('hex')),
            now,
            now
          ]
        );
        userRow = await db.get<UserRow>('SELECT * FROM users WHERE id = ?', [newId]);
      }

      const user = rowToAuthUser(userRow!);
      const { accessToken, refreshToken } = await createSessionTokens(db, user);
      setAuthCookies(res, accessToken, refreshToken);
      res.json({ user, accessToken, refreshToken });
    } catch (err) {
      next(err);
    }
  });

  router.post('/refresh', async (req, res, next) => {
    try {
      const parsed = refreshSchema.parse(req.body ?? {});
      const rawToken =
        parsed.refreshToken ||
        (req.cookies && typeof req.cookies.saathi_refresh_token === 'string'
          ? req.cookies.saathi_refresh_token
          : '');

      if (!rawToken) {
        throw new AppError(401, 'MISSING_REFRESH_TOKEN', 'Refresh token required.');
      }

      const tokenHash = hashOpaqueToken(rawToken);
      const tokenRow = await db.get<{
        id: string;
        user_id: string;
        expires_at: string;
        revoked_at: string | null;
      }>('SELECT * FROM refresh_tokens WHERE token_hash = ?', [tokenHash]);

      if (
        !tokenRow ||
        tokenRow.revoked_at !== null ||
        new Date(tokenRow.expires_at).getTime() < Date.now()
      ) {
        throw new AppError(401, 'INVALID_REFRESH_TOKEN', 'Refresh token is revoked or expired.');
      }

      // Rotate refresh token (revoke old one immediately)
      await db.run('UPDATE refresh_tokens SET revoked_at = ? WHERE id = ?', [
        new Date().toISOString(),
        tokenRow.id
      ]);

      const userRow = await db.get<UserRow>('SELECT * FROM users WHERE id = ?', [tokenRow.user_id]);
      if (!userRow || !userRow.is_active) {
        throw new AppError(401, 'USER_INACTIVE', 'User account is disabled.');
      }

      const user = rowToAuthUser(userRow);
      const { accessToken, refreshToken } = await createSessionTokens(db, user);
      setAuthCookies(res, accessToken, refreshToken);
      res.json({ user, accessToken, refreshToken });
    } catch (err) {
      next(err);
    }
  });

  router.post('/logout', async (req, res, next) => {
    try {
      const rawToken =
        (req.body && typeof req.body.refreshToken === 'string' ? req.body.refreshToken : '') ||
        (req.cookies && typeof req.cookies.saathi_refresh_token === 'string'
          ? req.cookies.saathi_refresh_token
          : '');

      if (rawToken) {
        await db.run('UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ?', [
          new Date().toISOString(),
          hashOpaqueToken(rawToken)
        ]);
      }

      res.clearCookie('saathi_access_token', { path: '/' });
      res.clearCookie('saathi_refresh_token', { path: '/' });
      res.json({ loggedOut: true });
    } catch (err) {
      next(err);
    }
  });

  router.get('/me', authenticateRequest, async (req: AuthenticatedRequest, res) => {
    res.json({ user: req.user });
  });

  return router;
}
