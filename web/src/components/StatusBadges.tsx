'use client';

import type { DocumentStatus, EligibilityOutcome } from '@shared/api-client';
import { useSaathi } from '@/context/SaathiProvider';

export function OutcomeBadge({ outcome }: { outcome: EligibilityOutcome }) {
  const { t } = useSaathi();

  const styles =
    outcome === 'ELIGIBLE'
      ? { bg: 'var(--saathi-eligible-bg)', fg: 'var(--saathi-eligible-fg)' }
      : outcome === 'NOT_ELIGIBLE'
        ? { bg: 'var(--saathi-not-eligible-bg)', fg: 'var(--saathi-not-eligible-fg)' }
        : { bg: 'var(--saathi-unknown-bg)', fg: 'var(--saathi-unknown-fg)' };

  return (
    <span
      className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold"
      style={{ backgroundColor: styles.bg, color: styles.fg }}
    >
      {t.outcomes[outcome]}
    </span>
  );
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  const { t } = useSaathi();
  const styles =
    status === 'HAVE'
      ? { bg: 'var(--saathi-eligible-bg)', fg: 'var(--saathi-eligible-fg)' }
      : status === 'MISSING'
        ? { bg: 'var(--saathi-not-eligible-bg)', fg: 'var(--saathi-not-eligible-fg)' }
        : { bg: 'var(--saathi-unknown-bg)', fg: 'var(--saathi-unknown-fg)' };

  return (
    <span
      className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold"
      style={{ backgroundColor: styles.bg, color: styles.fg }}
    >
      {t.documents[status]}
    </span>
  );
}
