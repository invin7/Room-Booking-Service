# Room Booking Service
A backend service for booking rooms with concurrency safety.

Topics: java, spring-boot, concurrency, postgresql, rest-api

## Stack

- Java 17+
- Spring Boot with Spring Web (REST)
- Spring Data JPA
- PostgreSQL
- Flyway for schema migrations
- JUnit 5
- Testcontainers for running tests against a real PostgreSQL instance
- Maven
- Docker and Docker Compose

## Domain

- **Room**: id, name, capacity.
- **Slot**: a bookable time window for a room, such as 10:00 to 11:00 on a given date.
- **Booking**: id, room, slot, user ID, status (CONFIRMED or CANCELLED), createdAt.
- A slot can have at most one CONFIRMED booking at a time. Cancelled bookings don't block the slot.

## API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/rooms` | GET | Get all rooms |
| `/rooms` | POST | Create a new room |
| `/rooms/{id}/availability` | GET | Get free slots for a room on a given date (query param: `date=YYYY-MM-DD`) |
| `/bookings` | POST | Book a slot (expects JSON with `roomId`, `startTime` (ISO format), `userId`) |
| `/bookings/{id}` | DELETE | Cancel a booking by setting its status to CANCELLED |
| `/users/{userId}/bookings` | GET | Get all bookings for a user |

## Concurrency Design

To prevent double bookings under concurrent requests, we enforce the rule at the database level using a unique partial index. We considered three approaches:

1. **Unique Constraint (Chosen Approach)**: 
   - We added a unique partial index on `(room_id, slot_id)` where `status = 'CONFIRMED'` in a Flyway migration.
   - The service attempts to create a booking and catches `DataIntegrityViolationException` when the constraint is violated, converting it to a 409 Conflict response.
   - This approach is safe because the database guarantees atomicity of the index check and update.

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
- The `booking` table has a unique partial index: `CREATE UNIQUE INDEX idx_booking_confirmed ON booking (room_id, slot_id) WHERE status = 'CONFIRMED';`
- When a booking is requested, the service:
  1. Validates inputs (room exists, start time is in the future, etc.)
  2. Finds or creates a slot for the given room and time range (with a fixed duration of 1 hour).
  3. Checks for an existing confirmed booking (service-level optimization, but not sufficient alone for concurrency safety).
  4. Attempts to insert a new booking with status `CONFIRMED`.
  5. If two requests reach step 4 concurrently, the database will allow only one to succeed; the other will fail with a unique constraint violation, which we catch and return as 409 Conflict.

## Setup and Running the Application

### Prerequisites

- Java 17+
- Maven
- Docker and Docker Compose (for running the application with PostgreSQL)
- Alternatively, you can run the tests which use Testcontainers to start a PostgreSQL instance.

### Running with Docker Compose

1. Clone the repository (or copy the source code).
2. Navigate to the project directory.
3. Run `docker-compose up --build`.
4. The application will be available at `http://localhost:8080`.

### Running Tests

The tests use Testcontainers, which requires Docker to be running.

1. Ensure Docker is running.
2. Run `mvn test`.
3. The tests will start a PostgreSQL container, run the application against it, and verify the behavior, including a concurrency test that simulates 50 simultaneous booking requests to ensure only one succeeds.


## License

This project is licensed under the MIT License.
