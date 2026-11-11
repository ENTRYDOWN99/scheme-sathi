import crypto from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import type { DatabaseClient } from '../db/database.js';
import { evaluateScheme } from '../domain/eligibilityEngine.js';
import {
  authenticateRequest,
  requireRole,
  resolveAccessibleUserId,
  type AuthenticatedRequest
} from '../middleware/auth.js';
import { AppError } from '../middleware/errorHandler.js';
import { computeSnapshotHash } from '../security/crypto.js';
import { recordAuditLog } from '../services/auditService.js';
import { getOrCreateProfile } from '../services/profileService.js';
import type {
  ApplicationStatus,
  EligibilityOutcome,
  GovernmentScheme,
  SchemeApplication
} from '../types/models.js';

interface ApplicationRow {
  id: string;
  user_id: string;
  scheme_id: string;
  scheme_name_en: string;
  status: ApplicationStatus;
  eligibility_outcome: EligibilityOutcome;
  human_confirmed: number;
  human_confirmed_timestamp: string | null;
  applicant_snapshot_hash: string;
  applicant_name: string;
  state_district: string;
  bank_ifsc_summary: string;
  checklist_summary: string;
  notes: string;
  handoff_pack_json: string | null;
  reviewer_id: string | null;
  review_note: string | null;
  created_at: string;
  updated_at: string;
}

const createAppSchema = z.object({
  userId: z.string().optional(),
  schemeId: z.string().min(1),
  applicantName: z.string().min(1).max(120),
  stateDistrict: z.string().min(1).max(120),
  bankIfscSummary: z.string().min(1).max(120),
  checklistSummary: z.string().min(1).max(200),
  notes: z.string().max(1000).optional()
});

const confirmAppSchema = z.object({
  confirmSnapshotHash: z.string().min(16),
  reviewedFields: z.literal(true),
  understandsAuthoritativePortal: z.literal(true),
  isAuthorizedHuman: z.literal(true)
});

const reviewAppSchema = z.object({
  decision: z.enum(['UNDER_REVIEW', 'APPROVED', 'REJECTED']),
  reviewNote: z.string().min(2).max(500)
});

function rowToApplication(row: ApplicationRow): SchemeApplication {
  return {
    id: row.id,
    userId: row.user_id,
    schemeId: row.scheme_id,
    schemeNameEn: row.scheme_name_en,
    status: row.status,
    eligibilityOutcome: row.eligibility_outcome,
    humanConfirmed: Boolean(row.human_confirmed),
    humanConfirmedTimestamp: row.human_confirmed_timestamp,
    applicantSnapshotHash: row.applicant_snapshot_hash,
    applicantName: row.applicant_name,
    stateDistrict: row.state_district,
    bankIfscSummary: row.bank_ifsc_summary,
    checklistSummary: row.checklist_summary,
    notes: row.notes,
    handoffPackJson: row.handoff_pack_json,
    reviewerId: row.reviewer_id,
    reviewNote: row.review_note,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

export function createApplicationsRouter(db: DatabaseClient): Router {
  const router = Router();

  router.get('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const requestedUserId = typeof req.query.userId === 'string' ? req.query.userId : undefined;
      let rows: ApplicationRow[];

      if (req.user!.role === 'CITIZEN') {
        rows = await db.query<ApplicationRow>(
          'SELECT * FROM applications WHERE user_id = ? ORDER BY updated_at DESC',
          [req.user!.id]
        );
      } else if (requestedUserId) {
        rows = await db.query<ApplicationRow>(
          'SELECT * FROM applications WHERE user_id = ? ORDER BY updated_at DESC',
          [requestedUserId]
        );
      } else {
        rows = await db.query<ApplicationRow>(
          'SELECT * FROM applications ORDER BY updated_at DESC'
        );
      }

      res.json({ applications: rows.map(rowToApplication) });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const parsed = createAppSchema.parse(req.body);
      const access = resolveAccessibleUserId(req, parsed.userId);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      const schemeRow = await db.get<{ payload_json: string }>(
        'SELECT payload_json FROM schemes WHERE id = ? OR code = ?',
        [parsed.schemeId, parsed.schemeId]
      );
      if (!schemeRow) {
        throw new AppError(404, 'SCHEME_NOT_FOUND', `Scheme '${parsed.schemeId}' not found.`);
      }
      const scheme = JSON.parse(schemeRow.payload_json) as GovernmentScheme;
      const profile = await getOrCreateProfile(db, access.userId, req.user?.displayName);
      const evaluation = evaluateScheme(profile, scheme);

      const snapshotHash = computeSnapshotHash({
        schemeId: scheme.id,
        ruleVersion: scheme.ruleVersion,
        applicantName: parsed.applicantName.trim(),
        stateDistrict: parsed.stateDistrict.trim(),
        bankIfscSummary: parsed.bankIfscSummary.trim(),
        checklistSummary: parsed.checklistSummary.trim()
      });

      const now = new Date().toISOString();
      const id = `app_${crypto.randomUUID()}`;
      const handoffPack = {
        applicationId: id,
        schemeId: scheme.id,
        schemeNameEn: scheme.nameEn,
        schemeNameHi: scheme.nameHi,
        officialPortalUrl: scheme.officialSourceUrl,
        ruleVersion: scheme.ruleVersion,
        eligibilityOutcome: evaluation.outcome,
        applicantName: parsed.applicantName.trim(),
        stateDistrict: parsed.stateDistrict.trim(),
        bankIfscSummary: parsed.bankIfscSummary.trim(),
        checklistSummary: parsed.checklistSummary.trim(),
        cscGuidanceEn: scheme.cscOfficeGuidanceEn,
        cscGuidanceHi: scheme.cscOfficeGuidanceHi,
        humanConfirmed: false,
        snapshotHash
      };

      await db.run(
        `INSERT INTO applications (
          id, user_id, scheme_id, scheme_name_en, status, eligibility_outcome,
          human_confirmed, human_confirmed_timestamp, applicant_snapshot_hash,
          applicant_name, state_district, bank_ifsc_summary, checklist_summary,
          notes, handoff_pack_json, reviewer_id, review_note, created_at, updated_at
        ) VALUES (?, ?, ?, ?, 'AWAITING_HUMAN_CONFIRMATION', ?, 0, NULL, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)`,
        [
          id,
          access.userId,
          scheme.id,
          scheme.nameEn,
          evaluation.outcome,
          snapshotHash,
          parsed.applicantName.trim(),
          parsed.stateDistrict.trim(),
          parsed.bankIfscSummary.trim(),
          parsed.checklistSummary.trim(),
          parsed.notes?.trim() ?? '',
          JSON.stringify(handoffPack),
          now,
          now
        ]
      );

      const saved = await db.get<ApplicationRow>('SELECT * FROM applications WHERE id = ?', [id]);
      await recordAuditLog(db, {
        actorId: req.user!.id,
        actorRole: req.user!.role,
        action: 'APPLICATION_DRAFT_CREATED',
        entityType: 'APPLICATION',
        entityId: id,
        details: `Created application draft for ${scheme.id} awaiting Human Confirmation Gate`
      });

      res.status(201).json({ application: rowToApplication(saved!) });
    } catch (err) {
      next(err);
    }
  });

  // Strict Human Confirmation Gate — AI can never call/bypass this; snapshot hash must match!
  router.post('/:id/confirm', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const parsed = confirmAppSchema.parse(req.body);
      const appRow = await db.get<ApplicationRow>('SELECT * FROM applications WHERE id = ?', [
        String(req.params.id)
      ]);
      if (!appRow) {
        throw new AppError(404, 'APPLICATION_NOT_FOUND', 'Application not found.');
      }
      if (req.user!.role === 'CITIZEN' && appRow.user_id !== req.user!.id) {
        throw new AppError(403, 'FORBIDDEN', 'Citizens can only confirm their own applications.');
      }
      if (parsed.confirmSnapshotHash !== appRow.applicant_snapshot_hash) {
        throw new AppError(
          409,
          'SNAPSHOT_HASH_MISMATCH',
          'Application snapshot changed or hash does not match. Re-verify fields before confirming.'
        );
      }

      const now = new Date().toISOString();
      const handoffObj = appRow.handoff_pack_json
        ? (JSON.parse(appRow.handoff_pack_json) as Record<string, unknown>)
        : {};
      handoffObj.humanConfirmed = true;
      handoffObj.humanConfirmedAt = now;
      handoffObj.confirmedByActorId = req.user!.id;
      handoffObj.confirmedByRole = req.user!.role;

      const nextStatus: ApplicationStatus =
        req.user!.role === 'CSC_OPERATOR' || req.user!.role === 'CITIZEN'
          ? 'SUBMITTED'
          : 'READY_FOR_HANDOFF';

      await db.run(
        `UPDATE applications SET
            status = ?,
            human_confirmed = 1,
            human_confirmed_timestamp = ?,
            handoff_pack_json = ?,
            updated_at = ?
           WHERE id = ?`,
        [nextStatus, now, JSON.stringify(handoffObj), now, appRow.id]
      );

      const updated = await db.get<ApplicationRow>('SELECT * FROM applications WHERE id = ?', [
        appRow.id
      ]);
      await recordAuditLog(db, {
        actorId: req.user!.id,
        actorRole: req.user!.role,
        action: 'APPLICATION_HUMAN_CONFIRMED',
        entityType: 'APPLICATION',
        entityId: appRow.id,
        details: `Human Confirmation Gate completed (${nextStatus})`
      });

      res.json({ application: rowToApplication(updated!) });
    } catch (err) {
      next(err);
    }
  });

  // Reviewer & Supervisor workflow decision (strictly blocked for CITIZEN and CSC_OPERATOR)
  router.patch(
    '/:id/review',
    authenticateRequest,
    requireRole('REVIEWER', 'SUPERVISOR'),
    async (req: AuthenticatedRequest, res, next) => {
      try {
        const parsed = reviewAppSchema.parse(req.body);
        const appRow = await db.get<ApplicationRow>('SELECT * FROM applications WHERE id = ?', [
          String(req.params.id)
        ]);
        if (!appRow) {
          throw new AppError(404, 'APPLICATION_NOT_FOUND', 'Application not found.');
        }
        if (!appRow.human_confirmed) {
          throw new AppError(
            409,
            'HUMAN_CONFIRMATION_REQUIRED',
            'Application has not passed Human Confirmation Gate yet.'
          );
        }

        const now = new Date().toISOString();
        await db.run(
          `UPDATE applications SET
            status = ?,
            reviewer_id = ?,
            review_note = ?,
            updated_at = ?
           WHERE id = ?`,
          [parsed.decision, req.user!.id, parsed.reviewNote.trim(), now, appRow.id]
        );

        const updated = await db.get<ApplicationRow>('SELECT * FROM applications WHERE id = ?', [
          appRow.id
        ]);
        await recordAuditLog(db, {
          actorId: req.user!.id,
          actorRole: req.user!.role,
          action: `APPLICATION_${parsed.decision}`,
          entityType: 'APPLICATION',
          entityId: appRow.id,
          details: `Reviewer decision ${parsed.decision}`
        });

        res.json({ application: rowToApplication(updated!) });
      } catch (err) {
        next(err);
      }
    }
  );

  return router;
}
