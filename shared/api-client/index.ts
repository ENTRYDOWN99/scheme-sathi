export type Role = 'CITIZEN' | 'CSC_OPERATOR' | 'SUPERVISOR' | 'REVIEWER';
export type LanguageCode = 'en' | 'hi';
export type EligibilityOutcome = 'ELIGIBLE' | 'NOT_ELIGIBLE' | 'UNKNOWN';
export type DocumentStatus = 'HAVE' | 'MISSING' | 'UNKNOWN';
export type ApplicationStatus =
  | 'DRAFT'
  | 'AWAITING_HUMAN_CONFIRMATION'
  | 'READY_FOR_HANDOFF'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED';

export interface AuthUser {
  id: string;
  email: string;
  phone: string;
  displayName: string;
  role: Role;
  preferredLanguage: LanguageCode;
}

export interface DocumentRequirement {
  id: string;
  nameEn: string;
  nameHi: string;
  mandatory: boolean;
  alternativesEn: string;
  alternativesHi: string;
  howToObtainEn: string;
  howToObtainHi: string;
}

export interface GovernmentScheme {
  id: string;
  code: string;
  nameEn: string;
  nameHi: string;
  category: string;
  benefitSummaryEn: string;
  benefitSummaryHi: string;
  descriptionEn: string;
  descriptionHi: string;
  officialSourceTitle: string;
  officialSourceUrl: string;
  verificationDate: string;
  isThresholdVerifiedOfficial: boolean;
  isRuleStaleWarning: boolean;
  ruleVersion: string;
  requiredDocuments: DocumentRequirement[];
  applicationInstructionsEn: string;
  applicationInstructionsHi: string;
  cscOfficeGuidanceEn: string;
  cscOfficeGuidanceHi: string;
}

export interface CitizenProfile {
  id: string;
  userId: string;
  displayName: string;
  phoneNumber?: string;
  dateOfBirth?: string;
  birthYear: number;
  ageConfirmedViaVoice: boolean;
  gender: string;
  state: string;
  district: string;
  urbanOrRural: string;
  annualIncome: number;
  bplStatus: string;
  socialCategory: string;
  disabilityStatus: string;
  ownsAgriculturalLand: string;
  hasBankAccount: string;
  isIncomeTaxPayer: string;
  hasLpgConnection: string;
  occupation: string;
  preferredLanguage: LanguageCode;
  updatedAt: string;
}

export interface ConditionResult {
  conditionId: string;
  field: string;
  outcome: EligibilityOutcome;
  reasonEn: string;
  reasonHi: string;
}

export interface EligibilityEvaluationResult {
  schemeId: string;
  schemeCode: string;
  ruleVersion: string;
  outcome: EligibilityOutcome;
  conditionResults: ConditionResult[];
  missingFields: string[];
  externalLookupGuidanceEn: string[];
  externalLookupGuidanceHi: string[];
  stateOverrideApplied?: string | null;
  evaluatedAt: string;
}

export interface DocumentChecklistItem {
  id: string;
  userId: string;
  schemeId: string;
  documentId: string;
  status: DocumentStatus;
  updatedAt: string;
}

export interface SchemeApplication {
  id: string;
  userId: string;
  schemeId: string;
  schemeNameEn: string;
  status: ApplicationStatus;
  eligibilityOutcome: EligibilityOutcome;
  humanConfirmed: boolean;
  humanConfirmedTimestamp?: string | null;
  applicantSnapshotHash: string;
  applicantName: string;
  stateDistrict: string;
  bankIfscSummary: string;
  checklistSummary: string;
  notes: string;
  handoffPackJson?: string | null;
  reviewerId?: string | null;
  reviewNote?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CollaborativeTask {
  id: string;
  userId: string;
  applicationId?: string | null;
  schemeId: string;
  schemeName: string;
  titleEn: string;
  titleHi: string;
  descriptionEn: string;
  descriptionHi: string;
  status: TaskStatus;
  assignedToRole: Role;
  assignedOperatorName: string;
  dueDateText: string;
  isNotificationUnread: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AiChatResponse {
  reply: string;
  modelUsed: string;
  usedFallback: boolean;
  errorCategory?: 'busy' | 'network' | 'generic';
  blockedBySafety: boolean;
  groundingSummary: string;
}

export interface AuditLogRow {
  id: string;
  actor_id: string;
  actor_role: Role;
  action: string;
  entity_type: string;
  entity_id: string;
  details: string;
  created_at: string;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export interface SchemeSaathiClientOptions {
  baseUrl: string;
  useCookies?: boolean;
  getToken?: () => string | null;
}

export class SchemeSaathiApiClient {
  private readonly baseUrl: string;
  private readonly useCookies: boolean;
  private readonly getToken?: () => string | null;

  constructor(options: SchemeSaathiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, '');
    this.useCookies = options.useCookies ?? true;
    this.getToken = options.getToken;
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (!headers.has('Content-Type') && init.body) {
      headers.set('Content-Type', 'application/json');
    }
    const token = this.getToken?.();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    const res = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      headers,
      credentials: this.useCookies ? 'include' : 'same-origin'
    });

    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    if (!res.ok) {
      throw new ApiClientError(
        res.status,
        String(data.error ?? 'API_ERROR'),
        String(data.message ?? `HTTP ${res.status}`)
      );
    }
    return data as T;
  }

  async demoLogin(role: Role): Promise<{ user: AuthUser }> {
    return this.request('/auth/demo-login', {
      method: 'POST',
      body: JSON.stringify({ role })
    });
  }

  async login(identifier: string, password: string): Promise<{ user: AuthUser }> {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ identifier, password })
    });
  }

  async requestOtp(phoneOrEmail: string): Promise<{ sent: boolean; devOtpHint?: string }> {
    return this.request('/auth/otp/request', {
      method: 'POST',
      body: JSON.stringify({ phoneOrEmail })
    });
  }

  async verifyOtp(phoneOrEmail: string, code: string): Promise<{ user: AuthUser }> {
    return this.request('/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ phoneOrEmail, code })
    });
  }

  async logout(): Promise<void> {
    await this.request('/auth/logout', { method: 'POST', body: JSON.stringify({}) });
  }

  async getMe(): Promise<{ user: AuthUser }> {
    return this.request('/auth/me');
  }

  async listSchemes(): Promise<{ schemes: GovernmentScheme[] }> {
    return this.request('/schemes');
  }

  async evaluateEligibility(
    schemeId?: string,
    userId?: string
  ): Promise<{ userId: string; results: EligibilityEvaluationResult[] }> {
    return this.request('/schemes/evaluate', {
      method: 'POST',
      body: JSON.stringify({ schemeId, userId })
    });
  }

  async getProfile(userId?: string): Promise<{ profile: CitizenProfile }> {
    const q = userId ? `?userId=${encodeURIComponent(userId)}` : '';
    return this.request(`/profile${q}`);
  }

  async updateProfile(patch: Partial<CitizenProfile>): Promise<{ profile: CitizenProfile }> {
    return this.request('/profile', {
      method: 'PUT',
      body: JSON.stringify(patch)
    });
  }

  async exportMyData(): Promise<Record<string, unknown>> {
    return this.request('/profile/export');
  }

  async deleteMyProfile(): Promise<{ deleted: boolean }> {
    return this.request('/profile', { method: 'DELETE' });
  }

  async getChecklists(schemeId?: string): Promise<{ items: DocumentChecklistItem[] }> {
    const q = schemeId ? `?schemeId=${encodeURIComponent(schemeId)}` : '';
    return this.request(`/checklists${q}`);
  }

  async setDocumentStatus(
    schemeId: string,
    documentId: string,
    status: DocumentStatus
  ): Promise<{ item: DocumentChecklistItem }> {
    return this.request('/checklists', {
      method: 'PUT',
      body: JSON.stringify({ schemeId, documentId, status })
    });
  }

  async listApplications(userId?: string): Promise<{ applications: SchemeApplication[] }> {
    const q = userId ? `?userId=${encodeURIComponent(userId)}` : '';
    return this.request(`/applications${q}`);
  }

  async createApplication(payload: {
    schemeId: string;
    applicantName: string;
    stateDistrict: string;
    bankIfscSummary: string;
    checklistSummary: string;
    notes?: string;
    userId?: string;
  }): Promise<{ application: SchemeApplication }> {
    return this.request('/applications', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  }

  async confirmApplication(
    id: string,
    confirmSnapshotHash: string
  ): Promise<{ application: SchemeApplication }> {
    return this.request(`/applications/${encodeURIComponent(id)}/confirm`, {
      method: 'POST',
      body: JSON.stringify({
        confirmSnapshotHash,
        reviewedFields: true,
        understandsAuthoritativePortal: true,
        isAuthorizedHuman: true
      })
    });
  }

  async reviewApplication(
    id: string,
    decision: 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED',
    reviewNote: string
  ): Promise<{ application: SchemeApplication }> {
    return this.request(`/applications/${encodeURIComponent(id)}/review`, {
      method: 'PATCH',
      body: JSON.stringify({ decision, reviewNote })
    });
  }

  async listTasks(): Promise<{ tasks: CollaborativeTask[] }> {
    return this.request('/tasks');
  }

  async updateTask(
    id: string,
    patch: { status?: TaskStatus; isNotificationUnread?: boolean }
  ): Promise<{ task: CollaborativeTask }> {
    return this.request(`/tasks/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch)
    });
  }

  async chatAi(prompt: string, language: LanguageCode): Promise<AiChatResponse> {
    return this.request('/ai/chat', {
      method: 'POST',
      body: JSON.stringify({ prompt, language })
    });
  }

  async voiceAge(
    utterance: string,
    language: LanguageCode
  ): Promise<{ detectedAge: number; estimatedBirthYear: number; confirmationPrompt: string }> {
    return this.request('/ai/voice-age', {
      method: 'POST',
      body: JSON.stringify({ utterance, language })
    });
  }

  async getAuditLogs(limit = 50): Promise<{ logs: AuditLogRow[] }> {
    return this.request(`/audit?limit=${limit}`);
  }

  async getAuditMetrics(): Promise<{
    applicationsByStatus: Array<{ status: string; count: number }>;
    tasksByStatus: Array<{ status: string; count: number }>;
    activeUsers: number;
  }> {
    return this.request('/audit/metrics');
  }
}
