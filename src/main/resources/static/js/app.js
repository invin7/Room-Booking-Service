import { session, setUnauthorizedHandler } from './api.js';
import { adminView } from './admin.js';
import { authView } from './auth.js';
import { bookingsView } from './bookings.js';
import { roomDetailView } from './roomDetail.js';
import { roomsView } from './rooms.js';
import { state } from './state.js';
import { h, toast } from './ui.js';

const main = document.getElementById('main');
const topbar = document.getElementById('topbar');

// path pattern -> view. `admin` routes need the ADMIN role, `public` ones work signed out.
const routes = [
  { pattern: /^\/login$/, view: authView, title: 'Sign in', public: true },
  { pattern: /^\/rooms$/, view: roomsView, title: 'Rooms' },
  { pattern: /^\/rooms\/(\d+)$/, view: roomDetailView, title: 'Room', params: ['id'] },
  { pattern: /^\/bookings$/, view: bookingsView, title: 'My bookings' },
  { pattern: /^\/admin$/, view: adminView, title: 'Admin', admin: true },
];

let renderId = 0;

function currentPath() {
  return location.hash.replace(/^#/, '') || '/';
}

function navigate(path) {
  if (currentPath() === path) render();
  else location.hash = path;
}

function signOut(message) {
  session.clear();
  state.user = null;
  if (message) toast(message, 'error');
  navigate('/login');
}

function onAuth(auth) {
  session.save(auth);
  state.user = auth.user;
  navigate('/rooms');
}

setUnauthorizedHandler(() => signOut('Your session has expired. Please sign in again.'));

function renderTopbar() {
  const path = currentPath();
  const link = (href, label) => h('a', { href: `#${href}`, 'aria-current': path === href || (href === '/rooms' && path.startsWith('/rooms/')) ? 'page' : null }, label);
  const user = state.user;
  topbar.replaceChildren(h('div', { class: 'topbar-inner' },
    h('a', { class: 'brand', href: '#/' },
      h('span', { class: 'brand-mark', 'aria-hidden': 'true' },
        h('svg', { viewBox: '0 0 24 24' }, h('path', { d: 'M5 7h14v12H5zM5 11h14M9 4v3M15 4v3' }))),
      'Room Booking'),
    user ? h('nav', { class: 'links', 'aria-label': 'Main' },
      link('/rooms', 'Rooms'), link('/bookings', 'My bookings'), user.role === 'ADMIN' ? link('/admin', 'Admin') : null) : null,
    user ? h('div', { class: 'whoami' },
      h('span', { class: 'small' }, user.name, user.role === 'ADMIN' ? h('span', { class: 'chip', style: 'margin-left:.4rem' }, 'Admin') : null),
      h('button', { class: 'btn small', type: 'button', onclick: () => signOut() }, 'Sign out')) : null));
}

async function render() {
  const id = ++renderId;
  const path = currentPath();
  const route = routes.find((r) => r.pattern.test(path));

  if (!state.user && !(route && route.public)) return navigate('/login');
  if (state.user && (!route || path === '/login' || path === '/')) return navigate('/rooms');
  if (route.admin && state.user.role !== 'ADMIN') return navigate('/rooms');

  renderTopbar();
  document.title = `${route.title} · Room Booking`;
  const match = path.match(route.pattern);
  const params = Object.fromEntries((route.params || []).map((name, i) => [name, match[i + 1]]));
  const ctx = { root: main, params, stale: () => id !== renderId, onAuth, refresh: render };
  try {
    await route.view(ctx);
  } catch (err) {
    if (ctx.stale()) return;
    if (err.status === 404) {
      main.replaceChildren(h('div', { class: 'card empty' }, 'That page or room does not exist. ', h('a', { href: '#/rooms' }, 'Back to rooms')));
    } else if (err.status !== 401) {
      main.replaceChildren(h('div', { class: 'form-error' }, err.message || 'Something went wrong.'));
    }
  }
  if (!ctx.stale()) main.focus({ preventScroll: true });
}

async function start() {
  const saved = session.load();
  if (saved) {
    state.user = saved.user;
    try {
      // The token may be valid but the account changed (e.g. role); trust the server's view.
      const res = await fetch('/auth/me', { headers: { Authorization: `Bearer ${saved.token}` } });
      if (res.ok) state.user = await res.json();
      else if (res.status === 401) { session.clear(); state.user = null; }
    } catch { /* offline: keep the cached user, API calls will report errors */ }
  }
  window.addEventListener('hashchange', render);
  render();
}

start();
