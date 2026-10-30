import { api, ApiError } from './api.js';
import { busy, h } from './ui.js';

export function authView(ctx) {
  let mode = 'login';

  const render = () => {
    const error = h('div', { class: 'form-error', role: 'alert', hidden: true });
    const name = h('input', { id: 'name', autocomplete: 'name', required: true, maxlength: 255 });
    const email = h('input', { id: 'email', type: 'email', autocomplete: 'email', required: true, maxlength: 255 });
    const password = h('input', {
      id: 'password', type: 'password', required: true, maxlength: 72,
      autocomplete: mode === 'login' ? 'current-password' : 'new-password',
      minlength: mode === 'register' ? 8 : null,
    });
    const submit = h('button', { class: 'btn primary', type: 'submit' }, mode === 'login' ? 'Sign in' : 'Create account');

    const form = h('form', {
      novalidate: false,
      onsubmit: async (e) => {
        e.preventDefault();
        error.hidden = true;
        try {
          const auth = await busy(submit, () => mode === 'login'
            ? api.login(email.value, password.value)
            : api.register(name.value, email.value, password.value));
          ctx.onAuth(auth);
        } catch (err) {
          error.textContent = err instanceof ApiError ? err.message : 'Something went wrong. Please try again.';
          error.hidden = false;
        }
      },
    },
      error,
      mode === 'register' ? h('div', { class: 'field' }, h('label', { for: 'name' }, 'Name'), name) : null,
      h('div', { class: 'field' }, h('label', { for: 'email' }, 'Email'), email),
      h('div', { class: 'field' }, h('label', { for: 'password' }, 'Password'), password,
        mode === 'register' ? h('p', { class: 'hint' }, 'At least 8 characters.') : null),
      h('div', { class: 'row' }, submit));

    const tab = (id, label) => h('button', {
      type: 'button', role: 'tab', 'aria-selected': String(mode === id),
      onclick: () => { mode = id; render(); },
    }, label);

    ctx.root.replaceChildren(h('div', { class: 'auth-wrap' },
      h('h1', {}, 'Room Booking'),
      h('p', { class: 'muted' }, 'Reserve a meeting room by the hour.'),
      h('div', { class: 'card', style: 'margin-top:1rem' },
        h('div', { class: 'tabs', role: 'tablist' }, tab('login', 'Sign in'), tab('register', 'Create account')),
        form)));
    (mode === 'register' ? name : email).focus();
  };

  render();
}
