import crypto from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { config } from '../config/env.js';
import type { AuthUser, Role } from '../types/models.js';

export interface AuthenticatedRequest extends Request {
  user?: AuthUser;
}

export function issueAccessToken(user: AuthUser): string {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email,
      phone: user.phone,
      displayName: user.displayName,
      role: user.role,
      preferredLanguage: user.preferredLanguage
    },
    config.jwtSecret,
    { expiresIn: config.jwtAccessExpiresIn as jwt.SignOptions['expiresIn'] }
  );
}

export function generateRefreshTokenPair(): { raw: string; hash: string } {
  const raw = crypto.randomBytes(40).toString('hex');
  const hash = crypto.createHash('sha256').update(raw).digest('hex');
  return { raw, hash };
}

export function hashOpaqueToken(raw: string): string {
  return crypto.createHash('sha256').update(raw).digest('hex');
}

export function authenticateRequest(
  req: AuthenticatedRequest,
  res: Response,
  next: NextFunction
): void {
  const authHeader = req.headers.authorization;
  let token: string | undefined;

  if (authHeader && authHeader.startsWith('Bearer ')) {
    token = authHeader.slice('Bearer '.length).trim();
  } else if (req.cookies && typeof req.cookies.saathi_access_token === 'string') {
    token = req.cookies.saathi_access_token;
  }

  if (!token) {
    res.status(401).json({
      error: 'UNAUTHORIZED',
      message: 'Authentication required.'
    });
    return;
  }

  try {
    const decoded = jwt.verify(token, config.jwtSecret) as jwt.JwtPayload;
    req.user = {
      id: String(decoded.sub),
      email: String(decoded.email ?? ''),
      phone: String(decoded.phone ?? ''),
      displayName: String(decoded.displayName ?? ''),
      role: decoded.role as Role,
      preferredLanguage: (decoded.preferredLanguage === 'en' ? 'en' : 'hi')
    };
    next();
  } catch {
    res.status(401).json({
      error: 'INVALID_TOKEN',
      message: 'Session token is invalid or expired.'
    });
  }
}

export function requireRole(...allowedRoles: Role[]) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction): void => {
    if (!req.user) {
      res.status(401).json({ error: 'UNAUTHORIZED', message: 'Authentication required.' });
      return;
    }
    if (!allowedRoles.includes(req.user.role)) {
      res.status(403).json({
        error: 'FORBIDDEN',
        message: `Role ${req.user.role} is not authorized for this action.`
      });
      return;
    }
    next();
  };
}

export function resolveAccessibleUserId(
  req: AuthenticatedRequest,
  requestedUserId?: string | null
): { userId: string } | { errorStatus: number; message: string } {
  if (!req.user) {
    return { errorStatus: 401, message: 'Authentication required.' };
  }
  const target = requestedUserId && requestedUserId.trim().length > 0 ? requestedUserId.trim() : req.user.id;
  if (req.user.role === 'CITIZEN' && target !== req.user.id) {
    return { errorStatus: 403, message: 'Citizens can only access their own records.' };
  }
  return { userId: target };
}
