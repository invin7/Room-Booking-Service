// DOM helpers. Text is only ever inserted as text nodes, never as HTML, so user-supplied
// strings (room names, user names, ...) cannot inject markup.

const SVG_NS = 'http://www.w3.org/2000/svg';
const SVG_TAGS = new Set(['svg', 'path']);

export function h(tag, attrs = {}, ...children) {
  const el = SVG_TAGS.has(tag) ? document.createElementNS(SVG_NS, tag) : document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'class') el.setAttribute('class', value);
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2), value);
    else if (key === 'dataset') Object.assign(el.dataset, value);
    else if (value === true) el.setAttribute(key, '');
    else el.setAttribute(key, value);
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === undefined || child === null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

export function clear(el) {
  el.replaceChildren();
  return el;
}

export function toast(message, kind = '') {
  const el = h('div', { class: `toast ${kind}`, role: kind === 'error' ? 'alert' : 'status' }, message);
  document.getElementById('toasts').append(el);
  setTimeout(() => el.remove(), kind === 'error' ? 6000 : 3500);
}

/** Runs an async action while disabling the button, so double clicks cannot submit twice. */
export async function busy(button, action) {
  button.disabled = true;
  try { return await action(); } finally { button.disabled = false; }
}

/** Shows a dialog. `onDismiss` runs when it is closed with Escape or a click outside. */
export function modal({ title, body, actions, onDismiss }) {
  const previous = document.activeElement;
  const close = () => {
    document.removeEventListener('keydown', onKey);
    overlay.remove();
    if (previous && previous.focus) previous.focus();
  };
  const dismiss = () => { close(); if (onDismiss) onDismiss(); };
  const onKey = (e) => { if (e.key === 'Escape') dismiss(); };
  const dialog = h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
    h('h2', {}, title), body, actions ? h('div', { class: 'actions' }, actions(close)) : null);
  const overlay = h('div', { class: 'overlay', onmousedown: (e) => { if (e.target === overlay) dismiss(); } }, dialog);
  document.addEventListener('keydown', onKey);
  document.body.append(overlay);
  const focusable = dialog.querySelector('input, select, textarea, button.primary, button');
  if (focusable) focusable.focus();
  return close;
}

/** Resolves true if the user confirms, false if they decline or dismiss. */
export function confirmDialog({ title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel', danger = false }) {
  return new Promise((resolve) => {
    modal({
      title,
      body: h('p', {}, message),
      onDismiss: () => resolve(false),
      actions: (close) => [
        h('button', { class: 'btn', type: 'button', onclick: () => { close(); resolve(false); } }, cancelLabel),
        h('button', { class: `btn ${danger ? 'danger solid' : 'primary'}`, type: 'button',
          onclick: () => { close(); resolve(true); } }, confirmLabel),
      ],
    });
  });
}

// ---- dates and times, always rendered in the room's own time zone ----

const cache = new Map();
function formatter(key, options) {
  const k = key + JSON.stringify(options);
  if (!cache.has(k)) cache.set(k, new Intl.DateTimeFormat(undefined, options));
  return cache.get(k);
}

export const fmtTime = (iso, tz) => formatter('t', { hour: 'numeric', minute: '2-digit', timeZone: tz }).format(new Date(iso));
export const fmtDay = (iso, tz) => formatter('d', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', timeZone: tz }).format(new Date(iso));
export const fmtRange = (startIso, endIso, tz) => `${fmtTime(startIso, tz)} – ${fmtTime(endIso, tz)}`;

export function tzLabel(tz) {
  const part = new Intl.DateTimeFormat(undefined, { timeZone: tz, timeZoneName: 'short' })
    .formatToParts(new Date()).find((p) => p.type === 'timeZoneName');
  return part ? `${tz} (${part.value})` : tz;
}

/** YYYY-MM-DD for "now" in the given zone. */
export function todayIn(tz) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

export function addDays(ymd, days) {
  const [y, m, d] = ymd.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

export function longDate(ymd) {
  const [y, m, d] = ymd.split('-').map(Number);
  return new Intl.DateTimeFormat(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(Date.UTC(y, m - 1, d)));
}

export function hourLabel(hour) {
  return `${String(hour % 24).padStart(2, '0')}:00`;
}
