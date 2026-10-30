# Room Booking Service

A meeting-room booking app with accounts, an admin role, a web UI, and a booking engine that cannot double-book a room, even when many people click the same slot at once.

Topics: java, spring-boot, concurrency, postgresql, rest-api, jwt

## Features

- **Accounts**: sign up and sign in (BCrypt passwords, stateless JWT sessions). Regular users book for themselves; admins manage rooms and can cancel any booking.
- **Rooms**: name, capacity, location, description, a time zone and daily opening hours.
- **Hourly slots** shown in the room's own time zone, with available / booked / yours / past states.
- **Booking and cancelling** from a single-page UI that works on phones and in dark mode.
- **No double bookings**: enforced by the database, covered by a 50-way concurrent test.

## Quick start

With Docker:

```bash
docker compose up --build
```

Open <http://localhost:8080>. The compose file creates demo data:

| | |
|---|---|
| Admin login | `admin@example.com` / `admin12345` |
| Demo rooms | Atlas, Borealis, Cedar, Delta |

Anyone can sign up from the login page; sign-ups are always regular users. **These credentials are demo defaults**: set `APP_ADMIN_PASSWORD` and `APP_JWT_SECRET` before putting this anywhere public.

Without Docker you need Java 17+, Maven and a PostgreSQL database:

```bash
createdb roombooking
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/roombooking \
SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=postgres \
APP_ADMIN_EMAIL=admin@example.com APP_ADMIN_PASSWORD=change-me-please \
APP_SEED_DEMO_ROOMS=true \
mvn spring-boot:run
```

### Configuration

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Database connection | `localhost:5432/roombooking`, `postgres` / `postgres` |
| `APP_JWT_SECRET` | HS256 signing key, 32+ characters | random per start (logins reset on restart) |
| `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | Creates the first admin at startup if that account does not exist | none |
| `APP_SEED_DEMO_ROOMS` | Adds four demo rooms when the database has no rooms | `false` |

There is deliberately no way to become an admin through the API; the first admin comes from the settings above.

## API

All endpoints except `/auth/register` and `/auth/login` need `Authorization: Bearer <token>`. Errors are JSON: `{"message": "...", "status": 409}`.

| Endpoint | Method | Who | Description |
|---|---|---|---|
| `/auth/register` | POST | anyone | Create an account (`name`, `email`, `password` of 8-72 chars). Returns a token. |
| `/auth/login` | POST | anyone | `email`, `password`. Returns `{token, expiresAt, user}`. |
| `/auth/me` | GET | signed in | The current user. |
| `/rooms` | GET | signed in | All rooms. |
| `/rooms/{id}` | GET | signed in | One room. |
| `/rooms` | POST | admin | Create a room. |
| `/rooms/{id}` | PUT | admin | Update a room. |
| `/rooms/{id}` | DELETE | admin | Delete a room. `409` while it has upcoming bookings. |
| `/rooms/{id}/availability?date=YYYY-MM-DD` | GET | signed in | Every bookable hour of that local date, each with `available`. |
| `/bookings` | POST | signed in | Book a slot: `roomId`, `startTime` (ISO-8601). The booker is the token's user. |
| `/bookings/{id}` | DELETE | owner or admin | Cancel. `403` for someone else's booking, `409` if already cancelled. |
| `/bookings/me` | GET | signed in | Your bookings, most recent first. |
| `/bookings` | GET | admin | All upcoming confirmed bookings. |

Room fields: `name`, `capacity`, optional `description`, `location`, `timezone` (IANA id, default `UTC`), `openHour` (default 8), `closeHour` (default 18, exclusive).

### Slots

A slot is one hour starting on the hour **in the room's time zone**, between `openHour` and `closeHour`. A `startTime` that is off the hour, outside opening hours, or in the past gets `400`. Slot rows are created lazily on first booking, and reading availability never writes.

## Domain

- **Room**: id, name, capacity, description, location, timezone, open and close hour.
- **Slot**: a one-hour window for a room.
- **Booking**: room, slot, user, status (`CONFIRMED` or `CANCELLED`), createdAt.
- **User**: name, email (unique, case-insensitive), role (`ADMIN` or `USER`).
- A slot can have at most one `CONFIRMED` booking. Cancelled bookings don't block it.

## Concurrency Design

To prevent double bookings under concurrent requests, we enforce the rule at the database level using a unique partial index. We considered three approaches:

1. **Unique Constraint (Chosen Approach)**:
   - A unique partial index on `slot_id` where `status = 'CONFIRMED'` (Flyway migration V3).
   - The service performs `INSERT ... ON CONFLICT ... DO NOTHING` using that index. A zero-row result becomes a 409 Conflict response.
   - This approach is safe because the database guarantees atomicity of the index check and insert.

2. **Pessimistic Locking (`SELECT ... FOR UPDATE`)**:
   - We could lock the slot row during the booking transaction to prevent other transactions from reading it until the lock is released.
   - This approach can lead to lower concurrency and potential deadlocks if not managed carefully.

3. **Optimistic Locking (`@Version`)**:
   - We could add a version column to the booking table and increment it on each update.
   - However, this does not directly prevent two bookings for the same slot; it would only detect an update conflict after the fact.
   - To use optimistic locking for this constraint, we would need to lock a derived table or use a more complex setup, which is less straightforward.

We chose the unique constraint approach because it is simple, performs well, and delegates the correctness guarantee to the database, which is the single source of truth.

### How It Works

- The `slot` table has a unique constraint on `(room_id, start_time, end_time)` to prevent duplicate slots.
- The `booking` table has a unique partial index on `slot_id` for confirmed bookings.
- When a booking is requested, the service:
  1. Validates the request (room exists, on the hour in the room's zone, inside opening hours, in the future).
  2. Finds or creates the slot (`INSERT ... ON CONFLICT DO NOTHING`, then a read).
  3. Atomically inserts a `CONFIRMED` booking with `ON CONFLICT ... DO NOTHING`.
  4. If two requests reach that insert concurrently, PostgreSQL accepts exactly one; the other affects zero rows and receives 409 Conflict.

## Security notes

- Passwords are hashed with BCrypt; login takes the same time for unknown emails and wrong passwords, and returns the same message.
- Tokens last 12 hours and carry the user id and role. A role change takes effect when the token expires.
- The UI stores its token in `localStorage` and inserts all server data as text, never HTML.
- Not yet included: rate limiting on login, password reset, email verification.

## Tests

Integration tests run the whole app against a real PostgreSQL: auth, roles, validation, cancellation rules, and the concurrency test (50 simultaneous requests for one slot, exactly one succeeds).

```bash
mvn test                      # starts PostgreSQL with Testcontainers (needs Docker)
TEST_DB_URL=jdbc:postgresql://localhost:5432/roombooking_test mvn test   # or use your own database
```

With `TEST_DB_URL`, optionally set `TEST_DB_USER` and `TEST_DB_PASSWORD` (both default to `test`). The tests delete all rows in that database, so point it at a scratch one.

## Project layout

```
src/main/java/.../config       security and first-run setup
src/main/java/.../controller   REST endpoints
src/main/java/.../service      room, booking and auth logic
src/main/resources/db/migration  Flyway migrations
src/main/resources/static      the web UI (plain ES modules, no build step)
```

## License

This project is licensed under the MIT License.
