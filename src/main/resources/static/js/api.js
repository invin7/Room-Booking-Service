// Thin fetch wrapper: adds the bearer token and turns error responses into ApiError.

const SESSION_KEY = 'roombooking.session';

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export const session = {
  load() {
    try {
      const s = JSON.parse(localStorage.getItem(SESSION_KEY));
      if (s && s.token && new Date(s.expiresAt) > new Date()) return s;
    } catch { /* storage unavailable or corrupt: behave as signed out */ }
    return null;
  },
  save(auth) {
    try { localStorage.setItem(SESSION_KEY, JSON.stringify(auth)); } catch { /* ignore */ }
  },
  clear() {
    try { localStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
  },
};

let onUnauthorized = () => {};
export function setUnauthorizedHandler(fn) { onUnauthorized = fn; }

async function request(method, path, body, { auth = true } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const s = auth ? session.load() : null;
  if (s) headers.Authorization = `Bearer ${s.token}`;

  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError('Cannot reach the server. Check your connection and try again.', 0);
  }
  if (res.status === 204) return null;

  let data = null;
  try { data = await res.json(); } catch { /* empty or non-JSON body */ }
  if (!res.ok) {
    // An expired or revoked token on a normal call means the session is over.
    if (res.status === 401 && auth && s) onUnauthorized();
    throw new ApiError((data && data.message) || `Request failed (${res.status})`, res.status);
  }
  return data;
}

export const api = {
  register: (name, email, password) => request('POST', '/auth/register', { name, email, password }, { auth: false }),
  login: (email, password) => request('POST', '/auth/login', { email, password }, { auth: false }),

  rooms: () => request('GET', '/rooms'),
  room: (id) => request('GET', `/rooms/${id}`),
  availability: (id, date) => request('GET', `/rooms/${id}/availability?date=${encodeURIComponent(date)}`),
  createRoom: (room) => request('POST', '/rooms', room),
  updateRoom: (id, room) => request('PUT', `/rooms/${id}`, room),
  deleteRoom: (id) => request('DELETE', `/rooms/${id}`),

  book: (roomId, startTime) => request('POST', '/bookings', { roomId, startTime }),
  cancel: (id) => request('DELETE', `/bookings/${id}`),
  myBookings: () => request('GET', '/bookings/me'),
  upcomingBookings: () => request('GET', '/bookings'),
};
