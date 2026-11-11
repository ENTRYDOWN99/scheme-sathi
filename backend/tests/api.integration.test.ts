import request from 'supertest';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { createApp } from '../src/app.js';
import { createDatabase, type DatabaseClient } from '../src/db/database.js';
import { runMigrations } from '../src/db/migrate.js';
import { seedDatabase } from '../src/db/seed.js';

describe('SchemeSaathi API Integration Tests', () => {
  let db: DatabaseClient;
  let app: ReturnType<typeof createApp>;

  let citizenToken = '';
  let citizenRefreshToken = '';
  let cscToken = '';
  let reviewerToken = '';
  let supervisorToken = '';
  let citizenUserId = '';

  beforeAll(async () => {
    db = createDatabase(':memory:');
    await runMigrations(db);
    await seedDatabase(db);
    app = createApp(db);
  });

  afterAll(async () => {
    await db.close();
  });

  it('GET /health returns ok status', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
  });

  it('registers a citizen, supports refresh token rotation, and signs in role demo accounts', async () => {
    const reg = await request(app).post('/api/v1/auth/register').send({
      phone: '+919876543210',
      displayName: 'Sita Devi',
      password: 'Password@123',
      preferredLanguage: 'hi'
    });
    expect(reg.status).toBe(201);
    expect(reg.body.user.role).toBe('CITIZEN');
    citizenToken = reg.body.accessToken;
    citizenRefreshToken = reg.body.refreshToken;
    citizenUserId = reg.body.user.id;

    // Refresh token rotation
    const ref1 = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: citizenRefreshToken });
    expect(ref1.status).toBe(200);
    expect(ref1.body.accessToken).toBeTruthy();

    // Old refresh token cannot be reused
    const refReuse = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: citizenRefreshToken });
    expect(refReuse.status).toBe(401);

    citizenToken = ref1.body.accessToken;

    const cscRes = await request(app)
      .post('/api/v1/auth/demo-login')
      .send({ role: 'CSC_OPERATOR' });
    expect(cscRes.status).toBe(200);
    cscToken = cscRes.body.accessToken;

    const revRes = await request(app).post('/api/v1/auth/demo-login').send({ role: 'REVIEWER' });
    expect(revRes.status).toBe(200);
    reviewerToken = revRes.body.accessToken;

    const supRes = await request(app).post('/api/v1/auth/demo-login').send({ role: 'SUPERVISOR' });
    expect(supRes.status).toBe(200);
    supervisorToken = supRes.body.accessToken;
  });

  it('blocks Aadhaar numbers in payloads and validates DOB DD/MM/YYYY', async () => {
    const aadhaarTry = await request(app)
      .put('/api/v1/profile')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({ notes: '1234 5678 9012' });
    expect(aadhaarTry.status).toBe(400);
    expect(aadhaarTry.body.error).toBe('AADHAAR_FORBIDDEN');

    const badDob = await request(app)
      .put('/api/v1/profile')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({ dateOfBirth: '31/02/2000' });
    expect(badDob.status).toBe(400);
    expect(badDob.body.error).toBe('INVALID_DOB');

    const goodProfile = await request(app)
      .put('/api/v1/profile')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({
        dateOfBirth: '01/01/1964',
        gender: 'female',
        state: 'Uttar Pradesh',
        district: 'Lucknow',
        annualIncome: 40000,
        bplStatus: 'yes',
        ownsAgriculturalLand: 'yes',
        hasBankAccount: 'yes',
        isIncomeTaxPayer: 'no',
        hasLpgConnection: 'no'
      });
    expect(goodProfile.status).toBe(200);
    expect(goodProfile.body.profile.dateOfBirth).toBe('01/01/1964');
  });

  it('evaluates eligibility deterministically and persists document checklists', async () => {
    const evalRes = await request(app)
      .post('/api/v1/schemes/evaluate')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({});
    expect(evalRes.status).toBe(200);
    const pmKisan = evalRes.body.results.find(
      (r: { schemeId: string }) => r.schemeId === 'PM-KISAN'
    );
    const pmuy = evalRes.body.results.find((r: { schemeId: string }) => r.schemeId === 'PMUY');
    expect(pmKisan.outcome).toBe('ELIGIBLE');
    expect(pmuy.outcome).toBe('ELIGIBLE');

    const chkPut = await request(app)
      .put('/api/v1/checklists')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({ schemeId: 'PM-KISAN', documentId: 'doc_land_record', status: 'HAVE' });
    expect(chkPut.status).toBe(200);

    const chkGet = await request(app)
      .get('/api/v1/checklists?schemeId=PM-KISAN')
      .set('Authorization', `Bearer ${citizenToken}`);
    expect(chkGet.status).toBe(200);
    expect(chkGet.body.items[0].status).toBe('HAVE');
  });

  it('enforces Human Confirmation Gate with snapshot verification and RBAC on application reviews', async () => {
    const createRes = await request(app)
      .post('/api/v1/applications')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({
        schemeId: 'PM-KISAN',
        applicantName: 'Sita Devi',
        stateDistrict: 'Uttar Pradesh / Lucknow',
        bankIfscSummary: 'SBIN0009999',
        checklistSummary: '1 Have, 2 Missing'
      });
    expect(createRes.status).toBe(201);
    const appId = createRes.body.application.id;
    const hash = createRes.body.application.applicantSnapshotHash;

    // Wrong hash must fail
    const badConfirm = await request(app)
      .post(`/api/v1/applications/${appId}/confirm`)
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({
        confirmSnapshotHash: '0'.repeat(64),
        reviewedFields: true,
        understandsAuthoritativePortal: true,
        isAuthorizedHuman: true
      });
    expect(badConfirm.status).toBe(409);

    // Valid hash succeeds
    const okConfirm = await request(app)
      .post(`/api/v1/applications/${appId}/confirm`)
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({
        confirmSnapshotHash: hash,
        reviewedFields: true,
        understandsAuthoritativePortal: true,
        isAuthorizedHuman: true
      });
    expect(okConfirm.status).toBe(200);
    expect(okConfirm.body.application.humanConfirmed).toBe(true);

    // Citizen and CSC Operator cannot approve/review
    const citizenReviewTry = await request(app)
      .patch(`/api/v1/applications/${appId}/review`)
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({ decision: 'APPROVED', reviewNote: 'Self approve' });
    expect(citizenReviewTry.status).toBe(403);

    const cscReviewTry = await request(app)
      .patch(`/api/v1/applications/${appId}/review`)
      .set('Authorization', `Bearer ${cscToken}`)
      .send({ decision: 'APPROVED', reviewNote: 'CSC try' });
    expect(cscReviewTry.status).toBe(403);

    // Reviewer succeeds
    const revApprove = await request(app)
      .patch(`/api/v1/applications/${appId}/review`)
      .set('Authorization', `Bearer ${reviewerToken}`)
      .send({ decision: 'APPROVED', reviewNote: 'All handoff docs verified.' });
    expect(revApprove.status).toBe(200);
    expect(revApprove.body.application.status).toBe('APPROVED');
  });

  it('enforces RBAC on audit trail, AI safety block, voice age, and DPDP export/delete', async () => {
    const citizenAuditTry = await request(app)
      .get('/api/v1/audit')
      .set('Authorization', `Bearer ${citizenToken}`);
    expect(citizenAuditTry.status).toBe(403);

    const supAuditOk = await request(app)
      .get('/api/v1/audit')
      .set('Authorization', `Bearer ${supervisorToken}`);
    expect(supAuditOk.status).toBe(200);
    expect(supAuditOk.body.logs.length).toBeGreaterThan(0);

    const aiSafety = await request(app)
      .post('/api/v1/ai/chat')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({ prompt: 'Please submit my application automatically' });
    expect(aiSafety.status).toBe(200);
    expect(aiSafety.body.blockedBySafety).toBe(true);

    const voiceAge = await request(app)
      .post('/api/v1/ai/voice-age')
      .set('Authorization', `Bearer ${citizenToken}`)
      .send({ utterance: '65', language: 'hi' });
    expect(voiceAge.status).toBe(200);
    expect(voiceAge.body.detectedAge).toBe(65);

    const dpdpExport = await request(app)
      .get('/api/v1/profile/export')
      .set('Authorization', `Bearer ${citizenToken}`);
    expect(dpdpExport.status).toBe(200);
    expect(dpdpExport.body.userId).toBe(citizenUserId);

    const dpdpDelete = await request(app)
      .delete('/api/v1/profile')
      .set('Authorization', `Bearer ${citizenToken}`);
    expect(dpdpDelete.status).toBe(200);
    expect(dpdpDelete.body.deleted).toBe(true);
  });
});
