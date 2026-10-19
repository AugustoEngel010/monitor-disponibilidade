export function el(tag, attributes = {}, ...children) {
  const element = document.createElement(tag);
  for (const [key, value] of Object.entries(attributes)) {
    if (key.startsWith('on')) element.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === 'className') element.className = value;
    else if (key === 'disabled') element.disabled = value;
    else element.setAttribute(key, value);
  }
  element.append(...children.flat().filter(child => child !== null && child !== undefined));
  return element;
}

export const labels = {
  PAUSED: 'Pausado', ACTIVE: 'Ativo', SEM_DADOS: 'Sem observação corrente',
  SEM_DADOS_RECENTES: 'Dados antigos', CONFIRMANDO_QUEDA: 'Confirmando queda',
  CONFIRMANDO_RECUPERACAO: 'Confirmando recuperação', UP: 'Disponível', DOWN: 'Indisponível',
  UNKNOWN: 'Ainda não confirmado', FRESH: 'Dados recentes', STALE: 'Dados antigos',
  NEVER_OBSERVED: 'Sem observação corrente', OPEN: 'Aberto', CLOSED: 'Encerrado',
  PENDING: 'Pendente', SENDING: 'Enviando', ACCEPTED: 'Aceita pelo SMTP', EXHAUSTED: 'Esgotada',
  RECOVERY: 'Recuperação', SUCCESS: 'Sucesso', HTTP_FAILURE: 'Código HTTP inesperado',
  TIMEOUT: 'Tempo esgotado', DNS_FAILURE: 'Falha DNS', CONNECTION_FAILURE: 'Falha de conexão',
  TLS_FAILURE: 'Falha TLS', INTERNAL_ERROR: 'Erro interno', DESTINATION_BLOCKED: 'Destino bloqueado',
  CANCELLED: 'Cancelada', FAILED: 'Falhou', UNCERTAIN: 'Confirmação incerta'
};
export const label = value => labels[value] || value;
export const button = (text, action, attrs = {}) => el('button', {type: 'button', onClick: action, ...attrs}, text);
export const notice = (text, error = false) => el('p', {role: error ? 'alert' : 'status',
  className: error ? 'notice error' : 'notice'}, text);
export function time(value) {
  return value ? el('time', {datetime: value, title: value}, new Date(value).toLocaleString('pt-BR'))
    : document.createTextNode('Sem registro');
}
export const line = (name, value) => el('p', {}, el('strong', {}, name + ': '), value);
export function state(summary) {
  return el('div', {className: 'state'},
    el('span', {className: 'badge ' + summary.label, 'data-testid': 'state-label'}, label(summary.label)),
    line('Estado confirmado', label(summary.confirmedState)),
    line('Última observação', time(summary.lastObservedAt)),
    line('Incidente aberto', summary.currentIncident ? summary.currentIncident.id : 'não'));
}
export function pager(data, name, change) {
  return el('nav', {'aria-label': 'Paginação de ' + name, className: 'pagination'},
    button('Anterior', () => change(data.page - 1), {disabled: data.page <= 0}),
    el('span', {}, `Página ${data.totalPages ? data.page + 1 : 0} de ${data.totalPages} · ${data.totalElements} registros`),
    button('Próxima', () => change(data.page + 1), {disabled: data.page + 1 >= data.totalPages}));
}
export function feedback(text) {
  document.querySelector('#feedback').replaceChildren(notice(text));
}
