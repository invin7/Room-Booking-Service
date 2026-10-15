-- Create room table
CREATE TABLE room (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity INTEGER NOT NULL
);

-- Create slot table
CREATE TABLE slot (
    id SERIAL PRIMARY KEY,
    room_id INTEGER NOT NULL REFERENCES room(id) ON DELETE CASCADE,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    UNIQUE (room_id, start_time, end_time)
);

-- Create booking table
CREATE TABLE booking (
    id SERIAL PRIMARY KEY,
    room_id INTEGER NOT NULL REFERENCES room(id) ON DELETE CASCADE,
    slot_id INTEGER NOT NULL REFERENCES slot(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create a unique partial index to ensure only one confirmed booking per slot
CREATE UNIQUE INDEX idx_booking_confirmed ON booking (room_id, slot_id) WHERE status = 'CONFIRMED';