import { SchemeSaathiApiClient } from '@shared/api-client';

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api/v1';

export const apiClient = new SchemeSaathiApiClient({
  baseUrl: API_URL,
  useCookies: true
});
