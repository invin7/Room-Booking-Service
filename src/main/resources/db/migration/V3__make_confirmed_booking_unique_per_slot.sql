-- A slot already belongs to one room, so slot_id is the precise business key.
-- Rebuild the original partial index without changing an already-applied V1 migration.
DROP INDEX IF EXISTS idx_booking_confirmed;

CREATE UNIQUE INDEX idx_booking_confirmed
    ON booking (slot_id)
    WHERE status = 'CONFIRMED';
