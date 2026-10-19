import {get} from './api.js';
import {el, button, notice, state, pager, label, line, time} from './dom.js';
import {lifecycle} from './lifecycle.js';

const number = value => Number(value).toLocaleString('pt-BR', {maximumFractionDigits: 9});
const metric = (value, unit) => value === null || value === undefined ? 'Sem dados' : number(value) + unit;

function incidentView(incident) {
  return el('article', {className: 'record'},
    el('h3', {}, label(incident.status) + ' · ' + incident.id),
    line('Primeira falha', time(incident.firstFailureAt)),
    line('Queda confirmada', time(incident.downConfirmedAt)),
    line('Início da recuperação', time(incident.recoveryStartedAt)),
    line('Recuperação confirmada', time(incident.recoveredAt)),
    incident.status === 'OPEN'
      ? line('Idade do incidente aberto', metric(incident.ageSeconds, ' s'))
      : line('Duração final estimada', metric(incident.estimatedDurationSeconds, ' s')));
}

function checkView(check) {
  return el('article', {className: 'record'}, el('h3', {}, label(check.outcome)),
    line('Execução', check.executionId), line('Início', time(check.startedAt)),
    line('Término', time(check.finishedAt)), line('Código HTTP', check.httpStatus ?? 'Sem código'),
    line('Duração registrada (ns)', number(check.durationNanos)),
    line('Versão / geração', `${check.configurationVersion} / ${check.executionGeneration}`),
    ...(check.errorMessage ? [line('Informação', check.errorMessage)] : []));
}

function notificationView(notification) {
  return el('article', {className: 'record'},
    el('h3', {}, (notification.type === 'DOWN' ? 'Queda' : 'Recuperação') + ' · ' + label(notification.status)),
    line('Notificação', notification.id), line('Incidente', notification.incidentId),
    line('Transição', notification.transitionId), line('Destinatário', notification.recipient),
    line('Confirmação da transição', time(notification.confirmedAt)),
    line('Tentativas', String(notification.attemptCount)),
    line('Próxima tentativa', time(notification.nextAttemptAt)), line('Conclusão', time(notification.completedAt)),
    el('ol', {className: 'attempts'}, notification.attempts.map(attempt => el('li', {},
      el('strong', {}, `Tentativa ${attempt.attemptNumber}: ${label(attempt.outcome)}`),
      line('Início', time(attempt.startedAt)), line('Término', time(attempt.finishedAt)),
      line('Código sanitizado', attempt.errorCode ?? 'Sem erro')))));
}

function summaryView(summary) {
  const statistics = summary.statistics;
  return el('div', {}, state(summary),
    line('Atualizado em', time(summary.generatedAt)),
    line('Próxima verificação prevista', time(summary.nextCheckAt)),
    line('Confirmações de queda / recuperação', `${summary.failureCount} / ${summary.recoveryCount}`),
    el('div', {className: 'metrics'},
      el('div', {className: 'metric'}, el('h3', {}, 'Taxa de sucesso por amostragem'),
        el('p', {'data-testid': 'success-rate', className: 'metric-value'}, metric(statistics.successRatePercent, '%'))),
      el('div', {className: 'metric'}, el('h3', {}, 'Latência média dos sucessos'),
        el('p', {'data-testid': 'average-latency', className: 'metric-value'}, metric(statistics.averageResponseTimeMs, ' ms')))),
    el('p', {}, `${statistics.totalChecks} verificações · ${statistics.validSamples} amostras externas · `
      + `${statistics.successSamples} sucessos · ${statistics.latencySamples} amostras de latência`),
    el('p', {className: 'help'}, 'Métrica por amostragem. Não representa uptime temporal.'),
    ...(statistics.dataStatus === 'NO_DATA' ? [notice('Sem amostras externas na janela.')] : []),
    line('Janela UTC [início, fim)', `${summary.window.from} → ${summary.window.to}`),
    line('Cobertura conhecida desde', time(summary.coverageKnownFrom)),
    el('details', {}, el('summary', {}, `Períodos sem observação (${summary.noObservationPeriods.length})`),
      summary.noObservationPeriods.length
        ? el('ul', {}, summary.noObservationPeriods.map(period => el('li', {}, time(period.from), ' → ', time(period.to))))
        : el('p', {}, 'Sem lacunas no período conhecido.')),
    el('section', {'aria-label': 'Incidente atual'}, el('h3', {}, 'Incidente atual'),
      summary.currentIncident ? incidentView(summary.currentIncident) : el('p', {}, 'Nenhum incidente aberto.')),
    el('p', {className: 'help'}, 'Idade e duração dos incidentes incluem pausas e lacunas. São estimativas separadas da taxa de sucesso.'));
}

function selectField(id, title, options) {
  const input = el('select', {id, name: id, 'aria-describedby': id + '-error'},
    options.map(([value, text]) => el('option', {value}, text)));
  const error = el('span', {id: id + '-error', className: 'field-error'});
  return {input, error, node: el('div', {className: 'field'}, el('label', {for: id}, title), input, error)};
}

const resources = [
  ['checks', 'Verificações', checkView, [
    ['outcome', 'Resultado da verificação', ['SUCCESS', 'HTTP_FAILURE', 'TIMEOUT', 'DNS_FAILURE',
      'CONNECTION_FAILURE', 'TLS_FAILURE', 'INTERNAL_ERROR', 'DESTINATION_BLOCKED', 'CANCELLED']]]],
  ['incidents', 'Incidentes', incidentView, [['status', 'Estado do incidente', ['OPEN', 'CLOSED']]]],
  ['notifications', 'Notificações', notificationView, [
    ['status', 'Estado da notificação', ['PENDING', 'SENDING', 'ACCEPTED', 'EXHAUSTED']],
    ['type', 'Tipo da notificação', ['DOWN', 'RECOVERY']]]]
];

export async function details(root, id) {
  const prefix = `/monitors/${encodeURIComponent(id)}`;
  const monitor = await get(prefix);
  if (!root.isConnected) return;
  const period = {};
  const dateFields = {};
  const windowForm = el('form', {className: 'filters card'});
  for (const [name, title] of [['from', 'Início (ISO-8601 com fuso)'], ['to', 'Fim (ISO-8601 com fuso)']]) {
    const input = el('input', {id: name, name, type: 'text', placeholder: '2026-10-19T09:00:00-03:00',
      'aria-describedby': name + '-error'});
    const error = el('span', {id: name + '-error', className: 'field-error'});
    dateFields[name] = {input, error};
    windowForm.append(el('div', {className: 'field'}, el('label', {for: name}, title), input, error));
  }
  windowForm.append(el('button', {type: 'submit'}, 'Aplicar período'),
    el('p', {className: 'help'}, 'Em branco: últimas 24 horas. Início inclusivo, fim exclusivo. Horários apresentados no fuso do navegador.'),
    el('p', {className: 'help'}, 'Incidentes filtram a confirmação da queda; notificações filtram a confirmação da transição. O incidente atual permanece visível fora da janela.'));

  function dateErrors(error) {
    for (const [name, field] of Object.entries(dateFields)) {
      if (error.errors?.[name]) {
        field.error.textContent = error.errors[name];
        field.input.setAttribute('aria-invalid', 'true');
      }
    }
  }

  const summaryContent = el('div');
  const summary = el('section', {className: 'card', role: 'region', 'aria-label': 'Resumo'},
    el('h2', {}, 'Resumo'), summaryContent);
  let summaryRequest = 0;
  async function loadSummary() {
    const request = ++summaryRequest;
    summaryContent.replaceChildren(notice('Carregando resumo…'));
    try {
      const data = await get(prefix + '/summary?' + new URLSearchParams(period));
      if (root.isConnected && request === summaryRequest) summaryContent.replaceChildren(summaryView(data));
    } catch (error) {
      if (root.isConnected && request === summaryRequest) {
        dateErrors(error);
        summaryContent.replaceChildren(notice(error.message, true), button('Tentar novamente', loadSummary));
      }
    }
  }

  const reload = [loadSummary];
  const histories = resources.map(([resource, title, view, definitions]) => {
    const content = el('div');
    const filters = el('form', {className: 'filters'});
    const controls = {};
    for (const [name, text, values] of definitions) {
      const field = selectField(resource + '-' + name, text, [['', 'Todos'],
        ...values.map(value => [value, name === 'type' && value === 'DOWN' ? 'Queda' : label(value)])]);
      controls[name] = field;
      filters.append(field.node);
    }
    const size = selectField(resource + '-size', 'Registros por página de ' + title,
      ['1', '5', '20', '50', '100'].map(value => [value, value]));
    size.input.value = '20';
    controls.size = size;
    filters.append(size.node, el('button', {type: 'submit'}, 'Aplicar filtros'));
    const section = el('section', {role: 'region', 'aria-label': title, className: 'card history'},
      el('h2', {}, title), filters,
      ...(resource === 'notifications' ? [el('p', {className: 'help'}, 'Aceitação SMTP não confirma entrega na caixa postal.')] : []),
      content);
    let serial = 0;
    let applied = {size: '20'};
    async function load(page = 0) {
      const request = ++serial;
      content.replaceChildren(notice('Carregando ' + title.toLowerCase() + '…'));
      try {
        const params = new URLSearchParams({...period, ...applied, page});
        const data = await get(`${prefix}/${resource}?${params}`);
        if (!root.isConnected || request !== serial) return;
        content.replaceChildren(...(data.items.length ? data.items.map(view)
          : [notice('Nenhum registro para os filtros selecionados.')]),
        pager(data, title, next => load(next)));
      } catch (error) {
        if (!root.isConnected || request !== serial) return;
        dateErrors(error);
        for (const [name, field] of Object.entries(controls)) {
          if (error.errors?.[name]) {
            field.error.textContent = error.errors[name];
            field.input.setAttribute('aria-invalid', 'true');
          }
        }
        content.replaceChildren(notice(error.message, true), button('Tentar novamente', () => load(page)));
      }
    }
    filters.addEventListener('submit', event => {
      event.preventDefault();
      applied = {};
      for (const [name, field] of Object.entries(controls)) {
        field.error.textContent = '';
        field.input.removeAttribute('aria-invalid');
        if (field.input.value) applied[name] = field.input.value;
      }
      load();
    });
    reload.push(() => load());
    return section;
  });
  const loadAll = () => { for (const load of reload) load(); };
  windowForm.addEventListener('submit', event => {
    event.preventDefault();
    for (const [name, field] of Object.entries(dateFields)) {
      field.error.textContent = '';
      field.input.removeAttribute('aria-invalid');
      if (field.input.value) period[name] = field.input.value;
      else delete period[name];
    }
    loadAll();
  });
  root.replaceChildren(el('a', {href: '#/'}, '← Voltar aos monitores'),
    el('div', {className: 'page-heading'}, el('div', {}, el('p', {className: 'eyebrow'}, 'DETALHES DO MONITOR'),
      el('h1', {}, monitor.name), el('p', {className: 'url'}, monitor.url)),
    el('div', {className: 'actions'}, button('Atualizar', loadAll),
      el('a', {href: `#/edit/${id}`}, 'Editar'), lifecycle(monitor, loadAll))),
    windowForm, summary, ...histories);
  loadAll();
}
