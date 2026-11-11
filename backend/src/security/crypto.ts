import crypto from 'node:crypto';
import { config } from '../config/env.js';

const GCM_IV_LENGTH = 12;
const GCM_TAG_LENGTH = 16;

function getMasterKeyBuffer(hexKey: string = config.piiEncryptionKey): Buffer {
  if (/^[0-9a-fA-F]{64}$/.test(hexKey)) {
    return Buffer.from(hexKey, 'hex');
  }
  return crypto.createHash('sha256').update(hexKey).digest();
}

function deriveRecordKey(userId: string, hexKey?: string): Buffer {
  const master = getMasterKeyBuffer(hexKey);
  return crypto.createHmac('sha256', master).update(`scheme-saathi-pii-v2:${userId}`).digest();
}

export function encryptPii(plainText: string, userId: string, hexKey?: string): string {
  const trimmed = plainText.trim();
  if (!trimmed) return '';
  const iv = crypto.randomBytes(GCM_IV_LENGTH);
  const key = deriveRecordKey(userId, hexKey);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update(trimmed, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  const combined = Buffer.concat([iv, encrypted, authTag]);
  return `GCM1:${combined.toString('base64')}`;
}

export function decryptPii(payload: string, userId: string, hexKey?: string): string {
  if (!payload) return '';
  if (!payload.startsWith('GCM1:')) return payload;
  try {
    const raw = Buffer.from(payload.slice(5), 'base64');
    if (raw.length <= GCM_IV_LENGTH + GCM_TAG_LENGTH) return '';
    const iv = raw.subarray(0, GCM_IV_LENGTH);
    const authTag = raw.subarray(raw.length - GCM_TAG_LENGTH);
    const ciphertext = raw.subarray(GCM_IV_LENGTH, raw.length - GCM_TAG_LENGTH);
    const key = deriveRecordKey(userId, hexKey);
    const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
    decipher.setAuthTag(authTag);
    const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
    return decrypted.toString('utf8');
  } catch {
    return '';
  }
}

export function hashPassword(password: string): string {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(password, salt, 64).toString('hex');
  return `${salt}:${derived}`;
}

export function verifyPassword(password: string, storedHash: string): boolean {
  const [salt, keyHex] = storedHash.split(':');
  if (!salt || !keyHex) return false;
  const derived = crypto.scryptSync(password, salt, 64);
  const storedBuf = Buffer.from(keyHex, 'hex');
  if (derived.length !== storedBuf.length) return false;
  return crypto.timingSafeEqual(derived, storedBuf);
}

export function sha256Hex(value: string): string {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex');
}

/**
 * Matches Android SecurityUtils.computeSnapshotHash exactly:
 * canonical = [schemeId, ruleVersion, applicantName, stateDistrict, bankIfscSummary, checklistSummary].map(trim).join('|')
 * first 32 hex chars of SHA-256.
 */
export function computeSnapshotHash(params: {
  schemeId: string;
  ruleVersion: string;
  applicantName: string;
  stateDistrict: string;
  bankIfscSummary: string;
  checklistSummary: string;
}): string {
  const canonical = [
    params.schemeId.trim(),
    params.ruleVersion.trim(),
    params.applicantName.trim(),
    params.stateDistrict.trim(),
    params.bankIfscSummary.trim(),
    params.checklistSummary.trim()
  ].join('|');
  return sha256Hex(canonical).slice(0, 32);
}
