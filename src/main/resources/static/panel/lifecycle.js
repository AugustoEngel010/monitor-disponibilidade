import {write} from './api.js';
import {el, button, notice, feedback} from './dom.js';

export function lifecycle(monitor, confirmed) {
  let current = monitor;
  const messages = el('div', {'aria-live': 'polite'});
  const control = button(current.lifecycle === 'PAUSED' ? 'Retomar' : 'Pausar', async () => {
    if (control.disabled) return;
    const paused = current.lifecycle === 'PAUSED';
    control.disabled = true;
    control.textContent = paused ? 'Retomando…' : 'Pausando…';
    messages.replaceChildren();
    try {
      const result = await write(`/monitors/${current.id}/${paused ? 'resume' : 'pause'}`, 'POST');
      current = result;
      if (!control.isConnected) return;
      feedback(result.lifecycle === 'PAUSED' ? 'Monitor pausado.' : 'Monitor retomado. Aguarde nova observação.');
      await confirmed(result);
    } catch (error) {
      if (control.isConnected) messages.replaceChildren(notice(error.message, true));
    } finally {
      control.disabled = false;
      control.textContent = current.lifecycle === 'PAUSED' ? 'Retomar' : 'Pausar';
    }
  });
  return el('div', {className: 'lifecycle'}, control, messages);
}
