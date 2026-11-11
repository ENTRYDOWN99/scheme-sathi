import type { Metadata } from 'next';
import './globals.css';
import { SaathiProvider } from '@/context/SaathiProvider';
import { AppShell } from '@/components/AppShell';

export const metadata: Metadata = {
  title: 'SchemeSaathi — Bilingual Welfare Scheme Assistant',
  description:
    'Deterministic welfare eligibility check, document readiness checklist, AI bilingual assistant, and CSC guided handoff.',
  manifest: '/manifest.webmanifest'
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="hi">
      <body>
        <SaathiProvider>
          <AppShell>{children}</AppShell>
        </SaathiProvider>
      </body>
    </html>
  );
}
