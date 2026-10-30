import { api, ApiError } from './api.js';
import { busy, confirmDialog, fmtDay, fmtRange, h, hourLabel, modal, toast, tzLabel } from './ui.js';

export async function adminView(ctx) {
  let tab = 'rooms';
  const body = h('div');

  const draw = async () => {
    body.replaceChildren(h('p', { class: 'loading' }, 'Loading…'));
    try {
      const content = tab === 'rooms' ? await roomsTab(draw) : await bookingsTab(draw);
      if (!ctx.stale()) body.replaceChildren(content);
    } catch (err) {
      if (!ctx.stale()) body.replaceChildren(h('p', { class: 'form-error' }, err.message));
    }
  };

  const tabButton = (id, label) => h('button', {
    type: 'button', role: 'tab', 'aria-selected': String(tab === id),
    onclick: () => { tab = id; renderTabs(); draw(); },
  }, label);
  const tabs = h('div', { class: 'section-tabs', role: 'tablist' });
  const renderTabs = () => tabs.replaceChildren(tabButton('rooms', 'Rooms'), tabButton('bookings', 'Upcoming bookings'));
  renderTabs();

  ctx.root.replaceChildren(
    h('div', { class: 'page-head' }, h('div', {}, h('h1', {}, 'Admin'), h('p', { class: 'muted' }, 'Manage rooms and see everyone’s bookings.'))),
    tabs, body);
  await draw();
}

async function roomsTab(refresh) {
  const rooms = await api.rooms();
  const del = async (room) => {
    const ok = await confirmDialog({ title: 'Delete room', message: `Delete ${room.name}? This also removes its booking history.`,
      confirmLabel: 'Delete room', danger: true });
    if (!ok) return;
    try { await api.deleteRoom(room.id); toast('Room deleted.', 'success'); } catch (err) { toast(err.message, 'error'); }
    refresh();
  };
  return h('div', { class: 'stack' },
    h('div', { class: 'row' }, h('button', { class: 'btn primary', type: 'button', onclick: () => roomForm(null, refresh) }, 'Add room')),
    rooms.length
      ? h('div', { class: 'card table-wrap' }, h('table', {},
          h('thead', {}, h('tr', {}, ['Name', 'Location', 'Seats', 'Open', 'Time zone', ''].map((t) => h('th', {}, t)))),
          h('tbody', {}, rooms.map((r) => h('tr', {},
            h('td', {}, h('strong', {}, r.name)), h('td', {}, r.location || '—'), h('td', {}, r.capacity),
            h('td', {}, `${hourLabel(r.openHour)}–${hourLabel(r.closeHour)}`), h('td', {}, r.timezone),
            h('td', { class: 'actions' },
              h('button', { class: 'btn small', type: 'button', onclick: () => roomForm(r, refresh) }, 'Edit'), ' ',
              h('button', { class: 'btn small danger', type: 'button', onclick: () => del(r) }, 'Delete')))))))
      : h('div', { class: 'card empty' }, 'No rooms yet. Add the first one.'));
}

async function bookingsTab(refresh) {
  const bookings = await api.upcomingBookings();
  const cancel = async (b) => {
    const ok = await confirmDialog({ title: 'Cancel booking',
      message: `Cancel ${b.userName}’s booking of ${b.roomName} on ${fmtDay(b.startTime, b.roomTimezone)}, ${fmtRange(b.startTime, b.endTime, b.roomTimezone)}?`,
      confirmLabel: 'Cancel booking', cancelLabel: 'Keep it', danger: true });
    if (!ok) return;
    try { await api.cancel(b.id); toast('Booking cancelled.', 'success'); } catch (err) { toast(err.message, 'error'); }
    refresh();
  };
  return bookings.length
    ? h('div', { class: 'card table-wrap' }, h('table', {},
        h('thead', {}, h('tr', {}, ['When', 'Room', 'Booked by', ''].map((t) => h('th', {}, t)))),
        h('tbody', {}, bookings.map((b) => h('tr', {},
          h('td', {}, h('strong', {}, fmtDay(b.startTime, b.roomTimezone)), h('br'), h('span', { class: 'muted' }, fmtRange(b.startTime, b.endTime, b.roomTimezone))),
          h('td', {}, b.roomName),
          h('td', {}, b.userName, h('br'), h('span', { class: 'muted small' }, b.userEmail)),
          h('td', { class: 'actions' }, h('button', { class: 'btn small danger', type: 'button', onclick: () => cancel(b) }, 'Cancel')))))))
    : h('div', { class: 'card empty' }, 'No upcoming bookings.');
}

function roomForm(room, refresh) {
  const editing = Boolean(room);
  const zones = (Intl.supportedValuesOf ? Intl.supportedValuesOf('timeZone') : []).concat('UTC');
  const f = {
    name: h('input', { id: 'f-name', required: true, maxlength: 255, value: room?.name || '' }),
    capacity: h('input', { id: 'f-cap', type: 'number', min: 1, max: 1000, required: true, value: room?.capacity ?? 6 }),
    location: h('input', { id: 'f-loc', maxlength: 255, value: room?.location || '' }),
    description: h('textarea', { id: 'f-desc', maxlength: 1000 }, room?.description || ''),
    timezone: h('input', { id: 'f-tz', list: 'tz-list', maxlength: 64, value: room?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC' }),
    open: h('input', { id: 'f-open', type: 'number', min: 0, max: 23, required: true, value: room?.openHour ?? 9 }),
    close: h('input', { id: 'f-close', type: 'number', min: 1, max: 24, required: true, value: room?.closeHour ?? 18 }),
  };
  const error = h('div', { class: 'form-error', role: 'alert', hidden: true });
  const field = (label, input, id, hint) => h('div', { class: 'field' }, h('label', { for: id }, label), input, hint ? h('p', { class: 'hint' }, hint) : null);

  const form = h('form', { id: 'room-form' },
    error,
    field('Name', f.name, 'f-name'),
    h('div', { class: 'field-row' }, field('Seats', f.capacity, 'f-cap'), field('Location', f.location, 'f-loc')),
    field('Description', f.description, 'f-desc'),
    field('Time zone', f.timezone, 'f-tz', 'Slots start on the hour in this zone, e.g. Asia/Kolkata.'),
    h('div', { class: 'field-row' }, field('Opens (hour, 0–23)', f.open, 'f-open'), field('Closes (hour, 1–24)', f.close, 'f-close')),
    h('datalist', { id: 'tz-list' }, zones.map((z) => h('option', { value: z }))));

  modal({
    title: editing ? `Edit ${room.name}` : 'Add room',
    body: form,
    actions: (close) => {
      const save = h('button', { class: 'btn primary', type: 'submit', form: 'room-form' }, editing ? 'Save changes' : 'Add room');
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        error.hidden = true;
        const payload = {
          name: f.name.value, capacity: Number(f.capacity.value),
          location: f.location.value || null, description: f.description.value || null,
          timezone: f.timezone.value || null, openHour: Number(f.open.value), closeHour: Number(f.close.value),
        };
        try {
          await busy(save, () => editing ? api.updateRoom(room.id, payload) : api.createRoom(payload));
          close();
          toast(editing ? 'Room updated.' : 'Room added.', 'success');
          refresh();
        } catch (err) {
          error.textContent = err instanceof ApiError ? err.message : 'Could not save the room.';
          error.hidden = false;
        }
      });
      return [h('button', { class: 'btn', type: 'button', onclick: close }, 'Cancel'), save];
    },
  });
}
