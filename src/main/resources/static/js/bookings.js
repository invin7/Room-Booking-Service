import { api } from './api.js';
import { confirmDialog, fmtDay, fmtRange, h, toast } from './ui.js';

export async function bookingsView(ctx) {
  ctx.root.replaceChildren(h('p', { class: 'loading' }, 'Loading your bookings…'));
  const bookings = await api.myBookings();
  if (ctx.stale()) return;

  const now = Date.now();
  const upcoming = bookings.filter((b) => b.status === 'CONFIRMED' && Date.parse(b.endTime) > now)
    .sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime));
  const earlier = bookings.filter((b) => !upcoming.includes(b));

  ctx.root.replaceChildren(
    h('div', { class: 'page-head' }, h('div', {}, h('h1', {}, 'My bookings'))),
    section('Upcoming', upcoming, true, 'Nothing booked yet. Pick a room to get started.'),
    earlier.length ? section('Past and cancelled', earlier, false) : null);

  function section(heading, items, cancellable, emptyText) {
    return h('section', { class: 'stack', style: 'margin-bottom:2rem' },
      h('h2', {}, heading),
      items.length
        ? h('div', { class: 'list' }, items.map((b) => row(b, cancellable)))
        : h('div', { class: 'card empty' }, emptyText, ' ', h('a', { href: '#/rooms' }, 'Browse rooms')));
  }

  function row(b, cancellable) {
    const cancelled = b.status === 'CANCELLED';
    return h('div', { class: 'card item' },
      h('div', { class: 'what' },
        h('strong', {}, h('a', { href: `#/rooms/${b.roomId}`, style: 'color:inherit' }, b.roomName)),
        h('span', { class: 'muted' }, `${fmtDay(b.startTime, b.roomTimezone)} · ${fmtRange(b.startTime, b.endTime, b.roomTimezone)}`)),
      h('span', { class: `badge ${cancelled ? 'off' : cancellable ? 'ok' : 'off'}` }, cancelled ? 'Cancelled' : cancellable ? 'Confirmed' : 'Completed'),
      cancellable ? h('button', { class: 'btn danger small', type: 'button', onclick: () => cancel(b) }, 'Cancel') : null);
  }

  async function cancel(b) {
    const ok = await confirmDialog({
      title: 'Cancel booking',
      message: `Cancel ${b.roomName} on ${fmtDay(b.startTime, b.roomTimezone)}, ${fmtRange(b.startTime, b.endTime, b.roomTimezone)}? The slot becomes available to others.`,
      confirmLabel: 'Cancel booking', cancelLabel: 'Keep it', danger: true,
    });
    if (!ok) return;
    try {
      await api.cancel(b.id);
      toast('Booking cancelled.', 'success');
    } catch (err) {
      toast(err.message, 'error');
    }
    await ctx.refresh();
  }
}
