import type { NextFunction, Request, Response } from 'express';
import { redactPiiForLlm } from '../security/piiGuard.js';

export function requestLogger(req: Request, res: Response, next: NextFunction): void {
  const start = Date.now();
  res.on('finish', () => {
    if (process.env.NODE_ENV === 'test') return;
    const ms = Date.now() - start;
    const safePath = redactPiiForLlm(req.originalUrl);
    console.info(`[http] ${req.method} ${safePath} ${res.statusCode} ${ms}ms`);
  });
  next();
}
