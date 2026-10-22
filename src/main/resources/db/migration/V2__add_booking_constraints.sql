-- Every slot is canonical: one hour, beginning exactly on the hour.
ALTER TABLE slot
    ADD CONSTRAINT chk_slot_one_hour CHECK (end_time = start_time + INTERVAL '1 hour'),
    ADD CONSTRAINT chk_slot_starts_on_hour CHECK (
        date_trunc('hour', start_time AT TIME ZONE 'UTC') = start_time AT TIME ZONE 'UTC'
    );

CREATE INDEX idx_booking_slot_status ON booking (slot_id, status);
