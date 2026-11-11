import { Router } from 'express';
import { z } from 'zod';
import type { DatabaseClient } from '../db/database.js';
import { parseDob, validateDob } from '../domain/dateUtils.js';
import {
  authenticateRequest,
  resolveAccessibleUserId,
  type AuthenticatedRequest
} from '../middleware/auth.js';
import { AppError } from '../middleware/errorHandler.js';
import { encryptPii } from '../security/crypto.js';
import { recordAuditLog } from '../services/auditService.js';
import { getOrCreateProfile } from '../services/profileService.js';

const updateProfileSchema = z.object({
  userId: z.string().optional(),
  displayName: z.string().min(1).max(100).optional(),
  phoneNumber: z.string().max(20).optional(),
  dateOfBirth: z.string().max(10).optional(),
  birthYear: z.number().int().min(-1).max(2100).optional(),
  ageConfirmedViaVoice: z.boolean().optional(),
  gender: z.string().max(30).optional(),
  state: z.string().max(60).optional(),
  district: z.string().max(60).optional(),
  urbanOrRural: z.string().max(20).optional(),
  annualIncome: z.number().int().min(-1).max(100000000).optional(),
  bplStatus: z.string().max(20).optional(),
  socialCategory: z.string().max(30).optional(),
  disabilityStatus: z.string().max(20).optional(),
  ownsAgriculturalLand: z.string().max(20).optional(),
  hasBankAccount: z.string().max(20).optional(),
  isIncomeTaxPayer: z.string().max(20).optional(),
  hasLpgConnection: z.string().max(20).optional(),
  occupation: z.string().max(60).optional(),
  preferredLanguage: z.enum(['en', 'hi']).optional()
});

export function createProfileRouter(db: DatabaseClient): Router {
  const router = Router();

  router.get('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const requested = typeof req.query.userId === 'string' ? req.query.userId : undefined;
      const access = resolveAccessibleUserId(req, requested);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      const profile = await getOrCreateProfile(db, access.userId, req.user?.displayName);
      res.json({ profile });
    } catch (err) {
      next(err);
    }
  });

  router.put('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const parsed = updateProfileSchema.parse(req.body);
      const access = resolveAccessibleUserId(req, parsed.userId);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      const current = await getOrCreateProfile(db, access.userId, req.user?.displayName);

      const nextDob =
        parsed.dateOfBirth !== undefined ? parsed.dateOfBirth.trim() : (current.dateOfBirth ?? '');
      let nextBirthYear = parsed.birthYear !== undefined ? parsed.birthYear : current.birthYear;

      if (parsed.dateOfBirth !== undefined && nextDob.length > 0) {
        const dobCheck = validateDob(nextDob);
        if (dobCheck !== 'VALID') {
          throw new AppError(
            400,
            'INVALID_DOB',
            `Invalid dateOfBirth '${nextDob}' (status: ${dobCheck}). Expected valid DD/MM/YYYY.`
          );
        }
        const parsedDob = parseDob(nextDob);
        if (parsedDob) {
          nextBirthYear = parsedDob.year;
        }
      }

      const nextPhone =
        parsed.phoneNumber !== undefined ? parsed.phoneNumber.trim() : (current.phoneNumber ?? '');
      const now = new Date().toISOString();

      await db.run(
        `UPDATE citizen_profiles SET
          display_name = ?,
          encrypted_phone = ?,
          encrypted_dob = ?,
          birth_year = ?,
          age_confirmed_via_voice = ?,
          gender = ?,
          state = ?,
          district = ?,
          urban_or_rural = ?,
          annual_income = ?,
          bpl_status = ?,
          social_category = ?,
          disability_status = ?,
          owns_agricultural_land = ?,
          has_bank_account = ?,
          is_income_tax_payer = ?,
          has_lpg_connection = ?,
          occupation = ?,
          preferred_language = ?,
          updated_at = ?
         WHERE user_id = ?`,
        [
          parsed.displayName ?? current.displayName,
          encryptPii(nextPhone, access.userId),
          encryptPii(nextDob, access.userId),
          nextBirthYear,
          (parsed.ageConfirmedViaVoice ?? current.ageConfirmedViaVoice) ? 1 : 0,
          parsed.gender ?? current.gender,
          parsed.state ?? current.state,
          parsed.district ?? current.district,
          parsed.urbanOrRural ?? current.urbanOrRural,
          parsed.annualIncome ?? current.annualIncome,
          parsed.bplStatus ?? current.bplStatus,
          parsed.socialCategory ?? current.socialCategory,
          parsed.disabilityStatus ?? current.disabilityStatus,
          parsed.ownsAgriculturalLand ?? current.ownsAgriculturalLand,
          parsed.hasBankAccount ?? current.hasBankAccount,
          parsed.isIncomeTaxPayer ?? current.isIncomeTaxPayer,
          parsed.hasLpgConnection ?? current.hasLpgConnection,
          parsed.occupation ?? current.occupation,
          parsed.preferredLanguage ?? current.preferredLanguage,
          now,
          access.userId
        ]
      );

      if (parsed.preferredLanguage) {
        await db.run('UPDATE users SET preferred_language = ?, updated_at = ? WHERE id = ?', [
          parsed.preferredLanguage,
          now,
          access.userId
        ]);
      }

      const updated = await getOrCreateProfile(db, access.userId);
      await recordAuditLog(db, {
        actorId: req.user!.id,
        actorRole: req.user!.role,
        action: 'PROFILE_UPDATE',
        entityType: 'CITIZEN_PROFILE',
        entityId: access.userId,
        details: `Profile updated for user ${access.userId}`
      });

      res.json({ profile: updated });
    } catch (err) {
      next(err);
    }
  });

  // DPDP-aligned Data Export (JSON bundle for the authenticated citizen or operator target)
  router.get('/export', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const requested = typeof req.query.userId === 'string' ? req.query.userId : undefined;
      const access = resolveAccessibleUserId(req, requested);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      const profile = await getOrCreateProfile(db, access.userId, req.user?.displayName);
      const checklists = await db.query(
        'SELECT scheme_id, document_id, status, updated_at FROM document_checklists WHERE user_id = ?',
        [access.userId]
      );
      const applications = await db.query(
        'SELECT id, scheme_id, scheme_name_en, status, eligibility_outcome, human_confirmed, created_at, updated_at FROM applications WHERE user_id = ?',
        [access.userId]
      );
      const tasks = await db.query(
        'SELECT id, scheme_id, title_en, status, assigned_to_role, updated_at FROM tasks WHERE user_id = ?',
        [access.userId]
      );

      await recordAuditLog(db, {
        actorId: req.user!.id,
        actorRole: req.user!.role,
        action: 'DPDP_DATA_EXPORT',
        entityType: 'CITIZEN_PROFILE',
        entityId: access.userId,
        details: 'Citizen exported personal data bundle'
      });

      res.json({
        exportedAt: new Date().toISOString(),
        userId: access.userId,
        profile,
        checklists,
        applications,
        tasks
      });
    } catch (err) {
      next(err);
    }
  });

  // DPDP-aligned Delete Profile / Personal Data
  router.delete('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const requested = typeof req.query.userId === 'string' ? req.query.userId : undefined;
      const access = resolveAccessibleUserId(req, requested);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      await db.run('DELETE FROM document_checklists WHERE user_id = ?', [access.userId]);
      await db.run('DELETE FROM citizen_profiles WHERE user_id = ?', [access.userId]);

      await recordAuditLog(db, {
        actorId: req.user!.id,
        actorRole: req.user!.role,
        action: 'DPDP_DATA_DELETE',
        entityType: 'CITIZEN_PROFILE',
        entityId: access.userId,
        details: 'Citizen profile and checklist PII erased per DPDP request'
      });

      res.json({ deleted: true, userId: access.userId });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
