-- Rooms get descriptive fields, a time zone and daily opening hours (whole hours, local time).
-- Existing rooms keep today's behaviour: UTC, open all day.
ALTER TABLE room
    ADD COLUMN description VARCHAR(1000),
    ADD COLUMN location VARCHAR(255),
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN open_hour SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN close_hour SMALLINT NOT NULL DEFAULT 24,
    ADD CONSTRAINT chk_room_hours CHECK (open_hour >= 0 AND close_hour <= 24 AND open_hour < close_hour);

-- Slots are one hour long and start on the hour in the room's local time. For zones with a
-- 30-minute offset (e.g. Asia/Kolkata) that is not an hour boundary in UTC, so the UTC-hour
-- rule from V2 is relaxed to a quarter-hour boundary.
ALTER TABLE slot DROP CONSTRAINT chk_slot_starts_on_hour;
ALTER TABLE slot
    ADD CONSTRAINT chk_slot_starts_on_quarter_hour CHECK (
        date_trunc('minute', start_time) = start_time
        AND extract(minute FROM start_time AT TIME ZONE 'UTC')::int % 15 = 0
    );
