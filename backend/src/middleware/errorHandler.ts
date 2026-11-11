import type { NextFunction, Request, Response } from 'express';
import { ZodError } from 'zod';
import { assertNoAadhaarInPayload, redactPiiForLlm } from '../security/piiGuard.js';

export class AppError extends Error {
  readonly statusCode: number;
  readonly code: string;

  constructor(statusCode: number, code: string, message: string) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
  }
}

export function noAadhaarMiddleware(req: Request, _res: Response, next: NextFunction): void {
  if (req.body && typeof req.body === 'object') {
    assertNoAadhaarInPayload(req.body);
  }
  next();
}

export function notFoundHandler(_req: Request, res: Response): void {
  res.status(404).json({
    error: 'NOT_FOUND',
    message: 'Requested endpoint does not exist.'
  });
}

export function errorHandler(
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction
): void {
  if (err instanceof ZodError) {
    res.status(400).json({
      error: 'VALIDATION_ERROR',
      message: err.issues.map((i) => `${i.path.join('.') || 'body'}: ${i.message}`).join('; ')
    });
    return;
  }

  if (err && typeof err === 'object' && 'statusCode' in err) {
    const typed = err as { statusCode?: number; code?: string; message?: string };
    const status = typed.statusCode && typed.statusCode >= 400 ? typed.statusCode : 400;
    res.status(status).json({
      error: typed.code || 'BAD_REQUEST',
      message: redactPiiForLlm(typed.message || 'Request failed')
    });
    return;
  }

  const safeMsg = err instanceof Error ? redactPiiForLlm(err.message) : 'Unexpected server error';
  console.error('[api-error]', safeMsg);
  res.status(500).json({
    error: 'INTERNAL_SERVER_ERROR',
    message: 'An unexpected error occurred. Please try again.'
  });
}
