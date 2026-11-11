import { SchemeSaathiApiClient } from '@shared/api-client';

const configuredApiUrl = process.env.NEXT_PUBLIC_API_URL || '/api/v1';
const serverBackendBase = (process.env.BACKEND_URL || 'http://localhost:4000').replace(/\/$/, '');

export const API_URL =
  typeof window === 'undefined' && configuredApiUrl.startsWith('/')
    ? `${serverBackendBase}${configuredApiUrl}`
    : configuredApiUrl;

export const apiClient = new SchemeSaathiApiClient({
  baseUrl: API_URL,
  useCookies: true
});
