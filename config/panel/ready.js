import {request} from '@playwright/test';

export default async function ready() {
  const api = await request.newContext();
  const url = (process.env.PANEL_URL || 'http://panel-app:8080') + '/actuator/health/readiness';
  const deadline = Date.now() + 60000;
  try {
    while (Date.now() < deadline) {
      try { if ((await api.get(url, {timeout: 2000})).ok()) return; } catch { /* Startup in progress. */ }
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    throw new Error('Backend de testes não ficou pronto em 60 segundos.');
  } finally { await api.dispose(); }
}
