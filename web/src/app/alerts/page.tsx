'use client';

import React from 'react';
import Link from 'next/link';
import { Bell, CheckCircle2 } from 'lucide-react';
import type { TaskStatus } from '@shared/api-client';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';

export default function AlertsPage() {
  const { user, tasks, language, t, refreshSessionAndData } = useSaathi();

  if (!user) {
    return (
      <div className="saathi-card p-6 text-center space-y-3">
        <p className="font-semibold">
          {language === 'hi'
            ? 'अलर्ट और CSC कार्य देखने के लिए साइन इन करें।'
            : 'Please sign in to view alerts and follow-up tasks.'}
        </p>
        <Link href="/login" className="saathi-btn-primary inline-flex items-center px-5 py-2.5 rounded-xl font-bold text-sm">
          {t.nav.login}
        </Link>
      </div>
    );
  }

  const updateStatus = async (taskId: string, status: TaskStatus) => {
    await apiClient.updateTask(taskId, { status, isNotificationUnread: false });
    await refreshSessionAndData();
  };

  return (
    <div className="max-w-3xl mx-auto space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold flex items-center gap-2">
          <Bell className="w-5 h-5" style={{ color: 'var(--saathi-secondary)' }} />
          <span>{language === 'hi' ? 'सहयोगी कार्य और अलर्ट' : 'Alerts & Collaborative Tasks'}</span>
        </h1>
      </div>

      {tasks.length === 0 ? (
        <div className="saathi-card p-6 text-sm" style={{ color: 'var(--saathi-on-surface-muted)' }}>
          {language === 'hi' ? 'अभी कोई लंबित कार्य नहीं है।' : 'No pending tasks or alerts.'}
        </div>
      ) : (
        <div className="space-y-3">
          {tasks.map((task) => (
            <article key={task.id} className="saathi-card p-5 space-y-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2 text-xs font-bold uppercase" style={{ color: 'var(--saathi-primary)' }}>
                    <span>{task.schemeName}</span>
                    <span>•</span>
                    <span>{t.roles[task.assignedToRole]}</span>
                    {task.isNotificationUnread && (
                      <span
                        className="px-2 py-0.5 rounded-full text-[10px] text-white"
                        style={{ backgroundColor: 'var(--saathi-secondary)' }}
                      >
                        NEW
                      </span>
                    )}
                  </div>
                  <h2 className="font-bold text-base mt-1">
                    {language === 'hi' ? task.titleHi : task.titleEn}
                  </h2>
                  <p className="text-sm mt-1" style={{ color: 'var(--saathi-on-surface-muted)' }}>
                    {language === 'hi' ? task.descriptionHi : task.descriptionEn}
                  </p>
                </div>
                <span
                  className="px-3 py-1 rounded-full text-xs font-bold"
                  style={{ backgroundColor: 'var(--saathi-surface-variant)' }}
                >
                  {task.status}
                </span>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2 pt-2">
                <span className="text-xs" style={{ color: 'var(--saathi-on-surface-muted)' }}>
                  {task.assignedOperatorName} • {task.dueDateText}
                </span>

                <div className="flex gap-2">
                  {task.status !== 'COMPLETED' && (
                    <button
                      type="button"
                      onClick={() => void updateStatus(task.id, 'COMPLETED')}
                      className="saathi-btn-primary px-3.5 py-2 rounded-xl text-xs font-bold inline-flex items-center gap-1.5 cursor-pointer"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      <span>{language === 'hi' ? 'पूर्ण चिह्नित करें' : 'Mark Completed'}</span>
                    </button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
