import {get, write} from './api.js';
import {el, notice, feedback} from './dom.js';

const fields = [
  ['name', 'Nome', 'text', '', {maxlength: 100}],
  ['url', 'URL', 'url', '', {maxlength: 2048}],
  ['intervalSeconds', 'Intervalo (segundos)', 'number', 60, {min: 10, max: 3600}],
  ['timeoutSeconds', 'Timeout (segundos)', 'number', 5, {min: 1, max: 30}],
  ['expectedStatus', 'Código HTTP esperado', 'number', 200, {min: 200, max: 599}],
  ['failureThreshold', 'Falhas para confirmar queda', 'number', 3, {min: 1, max: 10}],
  ['recoveryThreshold', 'Sucessos para confirmar recuperação', 'number', 2, {min: 1, max: 10}],
  ['alertRecipient', 'Destinatário dos alertas', 'email', '', {maxlength: 254}]
];

const translations = {
  'must contain 1 to 100 characters': 'Informe um nome com 1 a 100 caracteres.',
  'must be HTTP/HTTPS without credentials, query or fragment': 'Use HTTP/HTTPS sem credenciais, query ou fragmento.',
  'must be a single email up to 254 characters': 'Informe um único e-mail com até 254 caracteres.',
  'must be less than intervalSeconds': 'Deve ser menor que o intervalo.',
  'must be an integer': 'Informe um número inteiro.',
  'must be a string': 'Informe um texto.'
};

export async function form(root, id) {
  const monitor = id ? await get(`/monitors/${encodeURIComponent(id)}`) : null;
  if (!root.isConnected) return;
  const inputs = {};
  const errors = {};
  const messages = el('div', {tabindex: '-1'});
  const submit = el('button', {type: 'submit', className: 'primary'}, 'Salvar monitor');
  const editor = el('form', {novalidate: '', className: 'card editor'}, messages);
  for (const [name, title, type, initial, limits] of fields) {
    const errorId = 'error-' + name;
    const input = el('input', {id: name, name, type, ...limits,
      ...(type === 'number' ? {step: 1} : {}), required: '', 'aria-describedby': errorId});
    input.value = monitor ? monitor[name] : initial;
    const error = el('span', {id: errorId, className: 'field-error'});
    inputs[name] = input;
    errors[name] = error;
    editor.append(el('div', {className: 'field'}, el('label', {for: name}, title), input, error));
  }
  editor.append(el('p', {className: 'help'}, 'URL HTTP/HTTPS sem usuário, senha, query ou fragmento. Timeout menor que o intervalo. Um destinatário de e-mail.'),
    el('div', {className: 'actions'}, submit, el('a', {href: '#/'}, 'Cancelar')));
  editor.addEventListener('submit', async event => {
    event.preventDefault();
    if (submit.disabled) return;
    messages.replaceChildren();
    for (const name of Object.keys(inputs)) {
      inputs[name].removeAttribute('aria-invalid');
      errors[name].textContent = '';
    }
    const body = {};
    for (const [name, , type] of fields) {
      const value = inputs[name].value;
      body[name] = type === 'number' ? value === '' ? null : Number(value) : value;
    }
    submit.disabled = true;
    submit.textContent = 'Salvando…';
    try {
      const saved = await write(id ? `/monitors/${encodeURIComponent(id)}` : '/monitors', id ? 'PUT' : 'POST', body);
      if (!root.isConnected) return;
      feedback(id ? 'Monitor atualizado.' : 'Monitor cadastrado.');
      location.hash = `#/monitors/${saved.id}`;
    } catch (error) {
      if (!root.isConnected) return;
      messages.replaceChildren(notice(error.message, true));
      for (const [name, message] of Object.entries(error.errors || {})) {
        if (inputs[name]) {
          inputs[name].setAttribute('aria-invalid', 'true');
          errors[name].textContent = translations[message] || message.replace(
            /^must be between (\d+) and (\d+)$/, 'Informe um valor entre $1 e $2.');
        }
      }
      const first = Object.keys(inputs).find(name => errors[name].textContent);
      (first ? inputs[first] : messages).focus();
    } finally {
      submit.disabled = false;
      submit.textContent = 'Salvar monitor';
    }
  });
  root.replaceChildren(el('a', {href: '#/'}, '← Voltar aos monitores'),
    el('h1', {}, id ? 'Editar monitor' : 'Novo monitor'),
    ...(monitor?.openIncidentId ? [notice('Incidente aberto: preserve a política. Nome e destinatário podem ser alterados.')] : []),
    editor);
}
