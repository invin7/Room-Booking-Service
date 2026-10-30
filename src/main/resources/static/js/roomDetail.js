import { api, ApiError } from './api.js';
import { addDays, busy, confirmDialog, fmtDay, fmtRange, h, hourLabel, longDate, toast, todayIn, tzLabel } from './ui.js';

export async function roomDetailView(ctx) {
  ctx.root.replaceChildren(h('p', { class: 'loading' }, 'Loading room…'));
  const room = await api.room(ctx.params.id);
  if (ctx.stale()) return;

  const today = todayIn(room.timezone);
  let date = today;

  const slotsBox = h('div', { 'aria-live': 'polite' });
  const dateInput = h('input', {
    type: 'date', value: date, min: today, 'aria-label': 'Date',
    onchange: (e) => { if (e.target.value) go(e.target.value < today ? today : e.target.value); },
  });
  const prev = h('button', { class: 'btn', type: 'button', 'aria-label': 'Previous day', onclick: () => go(addDays(date, -1)) }, '‹');
  const next = h('button', { class: 'btn', type: 'button', 'aria-label': 'Next day', onclick: () => go(addDays(date, 1)) }, '›');
  const title = h('span', { class: 'date-title' });

  async function go(newDate) {
    date = newDate;
    dateInput.value = date;
    prev.disabled = date <= today;
    title.textContent = longDate(date);
    await load();
  }

  async function load() {
    const requested = date;
    slotsBox.replaceChildren(h('p', { class: 'loading' }, 'Loading availability…'));
    try {
      const [slots, mine] = await Promise.all([api.availability(room.id, requested), api.myBookings()]);
      if (ctx.stale() || requested !== date) return;
      const mineStarts = new Set(mine
        .filter((b) => b.roomId === room.id && b.status === 'CONFIRMED')
        .map((b) => Date.parse(b.startTime)));
      slotsBox.replaceChildren(h('div', { class: 'slot-grid' }, slots.map((s) => slotButton(s, mineStarts))));
    } catch (err) {
      if (ctx.stale()) return;
      slotsBox.replaceChildren(h('p', { class: 'form-error' }, err.message));
    }
  }

  function slotButton(slot, mineStarts) {
    const start = Date.parse(slot.startTime);
    const label = fmtRange(slot.startTime, slot.endTime, room.timezone);
    if (slot.available) {
      return h('button', { class: 'slot free', type: 'button', onclick: () => book(slot) },
        h('span', { class: 't' }, label), h('span', { class: 's' }, 'Available'));
    }
    if (mineStarts.has(start)) {
      return h('div', { class: 'slot mine' }, h('span', { class: 't' }, label), h('span', { class: 's' }, 'Your booking'));
    }
    const past = start <= Date.now();
    return h('div', { class: `slot ${past ? 'past' : 'taken'}` },
      h('span', { class: 't' }, label), h('span', { class: 's' }, past ? 'Past' : 'Booked'));
  }

  async function book(slot) {
    const ok = await confirmDialog({
      title: 'Confirm booking',
      message: `Book ${room.name} on ${fmtDay(slot.startTime, room.timezone)}, ${fmtRange(slot.startTime, slot.endTime, room.timezone)} (${room.timezone})?`,
      confirmLabel: 'Book it',
    });
    if (!ok) return;
    try {
      await api.book(room.id, slot.startTime);
      toast('Booked. You can manage it under My bookings.', 'success');
    } catch (err) {
      toast(err instanceof ApiError && err.status === 409 ? 'Someone just booked that slot.' : err.message, 'error');
    }
    await load();
  }

  ctx.root.replaceChildren(
    h('p', {}, h('a', { href: '#/rooms' }, '‹ All rooms')),
    h('div', { class: 'page-head', style: 'margin-top:.75rem' },
      h('div', {},
        h('h1', {}, room.name),
        h('p', { class: 'muted' }, [`${room.capacity} seats`, room.location, `open ${hourLabel(room.openHour)}–${hourLabel(room.closeHour)}`].filter(Boolean).join(' · ')),
        room.description ? h('p', { style: 'margin-top:.5rem' }, room.description) : null)),
    h('div', { class: 'card stack' },
      h('div', { class: 'datebar' }, prev, dateInput, next, title),
      h('p', { class: 'small muted' }, `Times are shown in ${tzLabel(room.timezone)}.`),
      slotsBox,
      h('div', { class: 'legend' },
        h('span', { class: 'l-free' }, 'Available'), h('span', { class: 'l-mine' }, 'Your booking'), h('span', { class: 'l-taken' }, 'Booked / past'))));

  await go(today);
}
