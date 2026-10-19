import {get} from './api.js';
import {el, button, notice, state, pager} from './dom.js';

const app = document.querySelector('#app');
let generation = 0;

async function list(root, page) {
  const content = el('div', {}, notice('Carregando monitores…'));
  root.append(el('div', {className: 'page-heading'}, el('div', {},
    el('p', {className: 'eyebrow'}, 'VISÃO GERAL'), el('h1', {}, 'Monitores')),
  el('div', {className: 'actions'}, button('Atualizar', render),
    el('a', {href: '#/new', className: 'primary'}, 'Novo monitor'))), content);
  try {
    const data = await get(`/monitors?page=${page}&size=20`);
    if (!root.isConnected) return;
    content.replaceChildren();
    if (!data.items.length) content.append(notice(data.totalElements
      ? 'Nenhum monitor nesta página.' : 'Nenhum monitor cadastrado. Comece em Novo monitor.'));
    const cards = el('div', {className: 'monitor-grid'});
    content.append(cards);
    for (const monitor of data.items) {
      const status = el('div', {}, notice('Carregando estado…'));
      const actions = el('div', {className: 'actions'},
        el('a', {href: `#/monitors/${monitor.id}`}, 'Detalhes'),
        el('a', {href: `#/edit/${monitor.id}`}, 'Editar'));
      const card = el('article', {className: 'card', 'data-testid': `monitor-${monitor.id}`},
        el('h2', {}, monitor.name), el('p', {className: 'url'}, monitor.url), status, actions);
      cards.append(card);
      get(`/monitors/${monitor.id}/summary`).then(summary => {
        if (root.isConnected) status.replaceChildren(state(summary));
      }).catch(error => {
        if (root.isConnected) status.replaceChildren(notice('Estado não carregado. ' + error.message, true));
      });
    }
    content.append(pager(data, 'monitores', next => { location.hash = `#/?page=${next}`; }));
  } catch (error) {
    if (root.isConnected) content.replaceChildren(notice(error.message, true));
  }
}

async function render() {
  const current = ++generation;
  const root = el('section');
  app.replaceChildren(root);
  app.focus({preventScroll: true});
  const hash = location.hash.slice(1) || '/';
  const [path, query] = hash.split('?');
  if (path === '/') {
    const value = new URLSearchParams(query).get('page') || '0';
    return list(root, /^\d+$/.test(value) ? value : '0');
  }
  root.append(el('a', {href: '#/'}, '← Voltar aos monitores'));
  root.append(notice('Carregando página…'));
  try {
    if (path === '/new' || path.startsWith('/edit/')) {
      const {form} = await import('./form.js');
      if (current === generation) await form(root, path.startsWith('/edit/') ? path.slice(6) : null);
    } else if (path.startsWith('/monitors/')) {
      const {details} = await import('./details.js');
      if (current === generation) await details(root, path.slice(10));
    } else root.replaceChildren(notice('Página não encontrada.', true), el('a', {href: '#/'}, 'Voltar aos monitores'));
  } catch (error) {
    if (current === generation) root.replaceChildren(notice(error.status !== undefined
      ? error.message : 'Não foi possível carregar esta página. Atualize para tentar novamente.', true),
    button('Atualizar', render), el('a', {href: '#/'}, 'Voltar aos monitores'));
  }
}

window.addEventListener('hashchange', render);
render();
