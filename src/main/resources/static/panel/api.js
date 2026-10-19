const base = '/api/admin';

export class ApiError extends Error {
  constructor(status, code, errors = {}) {
    const messages = {
      400: 'Revise os campos indicados.',
      401: 'Autenticação administrativa necessária. Recarregue a página para entrar.',
      403: 'Acesso negado ou proteção CSRF expirada. Confira os dados antes de tentar novamente.',
      404: 'Monitor não encontrado.',
      409: code === 'OPEN_INCIDENT'
        ? 'Há um incidente aberto. Altere somente nome e destinatário; a política deve ser preservada.'
        : 'Limite de 100 monitores atingido.'
    };
    super(messages[status] || 'Não foi possível consultar o backend. Tente atualizar os dados.');
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(base + path, {
      ...options, credentials: 'same-origin', cache: 'no-store',
      signal: AbortSignal.timeout(15000),
      headers: {Accept: 'application/json', ...options.headers}
    });
  } catch {
    throw new ApiError(0, 'NETWORK');
  }
  if (!response.ok) {
    let body = {};
    if (response.status === 400 || response.status === 409) {
      try { body = await response.json(); } catch { /* Status still describes the failure. */ }
    }
    throw new ApiError(response.status, body.code, body.errors);
  }
  return response.json();
}

export const get = path => request(path);

export async function write(path, method, body) {
  const token = await get('/csrf');
  try {
    return await request(path, {
      method,
      headers: {[token.headerName]: token.token, 'Content-Type': 'application/json'},
      ...(body === undefined ? {} : {body: JSON.stringify(body)})
    });
  } catch (error) {
    if (error.status === 0 || error.status >= 500) {
      error.message = 'Resultado da operação não confirmado. Confira os dados em Atualizar antes de repetir.';
    }
    throw error;
  }
}
