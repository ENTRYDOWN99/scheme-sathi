'use client';

import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import enStrings from '@shared/i18n/en.json';
import hiStrings from '@shared/i18n/hi.json';
import type {
  AuthUser,
  CitizenProfile,
  CollaborativeTask,
  DocumentChecklistItem,
  EligibilityEvaluationResult,
  GovernmentScheme,
  LanguageCode,
  Role,
  SchemeApplication
} from '@shared/api-client';
import { apiClient } from '@/lib/api';

type StringsDict = typeof enStrings;

interface SaathiContextValue {
  user: AuthUser | null;
  loadingAuth: boolean;
  language: LanguageCode;
  t: StringsDict;
  darkMode: boolean;
  largeText: boolean;
  schemes: GovernmentScheme[];
  profile: CitizenProfile | null;
  evaluations: EligibilityEvaluationResult[];
  checklists: DocumentChecklistItem[];
  applications: SchemeApplication[];
  tasks: CollaborativeTask[];
  setLanguage: (lang: LanguageCode) => void;
  toggleDarkMode: () => void;
  toggleLargeText: () => void;
  refreshSessionAndData: () => Promise<void>;
  demoSignIn: (role: Role) => Promise<void>;
  signOut: () => Promise<void>;
}

const SaathiContext = createContext<SaathiContextValue | undefined>(undefined);

export function SaathiProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [language, setLanguageState] = useState<LanguageCode>('hi');
  const [darkMode, setDarkMode] = useState(false);
  const [largeText, setLargeText] = useState(false);

  const [schemes, setSchemes] = useState<GovernmentScheme[]>([]);
  const [profile, setProfile] = useState<CitizenProfile | null>(null);
  const [evaluations, setEvaluations] = useState<EligibilityEvaluationResult[]>([]);
  const [checklists, setChecklists] = useState<DocumentChecklistItem[]>([]);
  const [applications, setApplications] = useState<SchemeApplication[]>([]);
  const [tasks, setTasks] = useState<CollaborativeTask[]>([]);

  useEffect(() => {
    const savedDark = window.localStorage.getItem('saathi_dark') === '1';
    const savedLarge = window.localStorage.getItem('saathi_large') === '1';
    const savedLang = window.localStorage.getItem('saathi_lang');
    setDarkMode(savedDark);
    setLargeText(savedLarge);
    if (savedLang === 'en' || savedLang === 'hi') {
      setLanguageState(savedLang);
    }
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => undefined);
    }
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle('dark', darkMode);
    root.classList.toggle('large-text', largeText);
    root.lang = language;
  }, [darkMode, largeText, language]);

  const setLanguage = useCallback((lang: LanguageCode) => {
    setLanguageState(lang);
    window.localStorage.setItem('saathi_lang', lang);
  }, []);

  const toggleDarkMode = useCallback(() => {
    setDarkMode((prev) => {
      const next = !prev;
      window.localStorage.setItem('saathi_dark', next ? '1' : '0');
      return next;
    });
  }, []);

  const toggleLargeText = useCallback(() => {
    setLargeText((prev) => {
      const next = !prev;
      window.localStorage.setItem('saathi_large', next ? '1' : '0');
      return next;
    });
  }, []);

  const refreshSessionAndData = useCallback(async () => {
    try {
      const schemeRes = await apiClient.listSchemes();
      setSchemes(schemeRes.schemes);
    } catch {
      // Backend might still be starting up
    }

    try {
      const meRes = await apiClient.getMe();
      setUser(meRes.user);
      const [profRes, evalRes, chkRes, appRes, taskRes] = await Promise.all([
        apiClient.getProfile(),
        apiClient.evaluateEligibility(),
        apiClient.getChecklists(),
        apiClient.listApplications(),
        apiClient.listTasks()
      ]);
      setProfile(profRes.profile);
      setEvaluations(evalRes.results);
      setChecklists(chkRes.items);
      setApplications(appRes.applications);
      setTasks(taskRes.tasks);
    } catch {
      setUser(null);
      setProfile(null);
      setEvaluations([]);
      setChecklists([]);
      setApplications([]);
      setTasks([]);
    } finally {
      setLoadingAuth(false);
    }
  }, []);

  useEffect(() => {
    void refreshSessionAndData();
  }, [refreshSessionAndData]);

  const demoSignIn = useCallback(
    async (role: Role) => {
      await apiClient.demoLogin(role);
      await refreshSessionAndData();
    },
    [refreshSessionAndData]
  );

  const signOut = useCallback(async () => {
    await apiClient.logout();
    setUser(null);
    setProfile(null);
    setEvaluations([]);
    setChecklists([]);
    setApplications([]);
    setTasks([]);
  }, []);

  const t = useMemo(() => (language === 'hi' ? hiStrings : enStrings), [language]);

  const value = useMemo(
    () => ({
      user,
      loadingAuth,
      language,
      t,
      darkMode,
      largeText,
      schemes,
      profile,
      evaluations,
      checklists,
      applications,
      tasks,
      setLanguage,
      toggleDarkMode,
      toggleLargeText,
      refreshSessionAndData,
      demoSignIn,
      signOut
    }),
    [
      user,
      loadingAuth,
      language,
      t,
      darkMode,
      largeText,
      schemes,
      profile,
      evaluations,
      checklists,
      applications,
      tasks,
      setLanguage,
      toggleDarkMode,
      toggleLargeText,
      refreshSessionAndData,
      demoSignIn,
      signOut
    ]
  );

  return <SaathiContext.Provider value={value}>{children}</SaathiContext.Provider>;
}

export function useSaathi() {
  const ctx = useContext(SaathiContext);
  if (!ctx) {
    throw new Error('useSaathi must be used inside SaathiProvider');
  }
  return ctx;
}
