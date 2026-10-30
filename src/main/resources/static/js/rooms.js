import { api } from './api.js';
import { h, hourLabel, tzLabel } from './ui.js';

export async function roomsView(ctx) {
  ctx.root.replaceChildren(h('p', { class: 'loading' }, 'Loading rooms…'));
  const rooms = await api.rooms();
  if (ctx.stale()) return;

  const grid = h('div', { class: 'grid' });
  const search = h('input', { type: 'search', placeholder: 'Search by name or location', 'aria-label': 'Search rooms' });
  const seats = h('select', { 'aria-label': 'Minimum capacity' },
    h('option', { value: '0' }, 'Any size'),
    [4, 8, 12, 20].map((n) => h('option', { value: String(n) }, `${n}+ seats`)));

  const draw = () => {
    const q = search.value.trim().toLowerCase();
    const min = Number(seats.value);
    const shown = rooms.filter((r) => r.capacity >= min
      && (!q || `${r.name} ${r.location || ''}`.toLowerCase().includes(q)));
    grid.replaceChildren(...(shown.length
      ? shown.map(roomCard)
      : [h('div', { class: 'card empty', style: 'grid-column:1/-1' },
          rooms.length ? 'No rooms match your filters.' : 'No rooms yet.')]));
  };
  search.addEventListener('input', draw);
  seats.addEventListener('change', draw);
  draw();

  ctx.root.replaceChildren(
    h('div', { class: 'page-head' },
      h('div', {}, h('h1', {}, 'Rooms'), h('p', { class: 'muted' }, 'Pick a room to see when it is free.')),
      h('div', { class: 'row' }, search, seats)),
    grid);
}

function roomCard(room) {
  return h('article', { class: 'card room-card' },
    h('h2', {}, h('a', { href: `#/rooms/${room.id}` }, room.name)),
    h('div', { class: 'meta' },
      h('span', { class: 'chip' }, `${room.capacity} seats`),
      room.location ? h('span', { class: 'chip' }, room.location) : null,
      h('span', { class: 'chip' }, `${hourLabel(room.openHour)}–${hourLabel(room.closeHour)}`)),
    room.description ? h('p', { class: 'muted' }, room.description) : null,
    h('p', { class: 'small muted' }, tzLabel(room.timezone)),
    h('a', { class: 'btn primary', href: `#/rooms/${room.id}` }, 'See availability'));
}
