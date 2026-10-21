import {pathToFileURL} from 'node:url';
import {api, ready} from './client.mjs';

export const configurations = ['main', 'secondary'].map(target => ({
  name: target === 'main' ? 'Demo principal' : 'Demo independente',
  url: `http://demo:8080/targets/${target}`, intervalSeconds: 10, timeoutSeconds: 2,
  expectedStatus: 200, failureThreshold: 3, recoveryThreshold: 2,
  alertRecipient: 'portfolio@example.test'
}));

export async function seed(call = api) {
  const existing = (await call('/api/admin/monitors?size=100')).items;
  const result = {};
  for (const config of configurations) {
    const matches = existing.filter(item => item.url === config.url);
    if (matches.length > 1) throw new Error('duplicate demo identity');
    if (matches.length && Object.keys(config).some(key => matches[0][key] !== config[key])) {
      throw new Error('incompatible demo configuration; use a new Compose project');
    }
    result[new URL(config.url).pathname.split('/').pop()] = matches[0]
      || await call('/api/admin/monitors', 'POST', config);
  }
  return result;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  await ready();
  const monitors = await seed();
  console.log(JSON.stringify({seed: 'OK', main: monitors.main.id, secondary: monitors.secondary.id}));
}
