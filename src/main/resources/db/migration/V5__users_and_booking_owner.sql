CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Emails are stored lower-cased; the index also guards against mixed-case duplicates.
CREATE UNIQUE INDEX idx_app_user_email ON app_user (lower(email));

-- Bookings used to carry a free-text user id that anyone could claim. They cannot be attributed
-- to an account, so bookings made before accounts existed are discarded.
DELETE FROM booking;
ALTER TABLE booking DROP COLUMN user_id;
ALTER TABLE booking ADD COLUMN user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE;
CREATE INDEX idx_booking_user ON booking (user_id);
