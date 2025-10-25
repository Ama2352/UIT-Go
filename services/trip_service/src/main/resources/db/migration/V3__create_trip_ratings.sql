CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS trip_ratings (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    rating INT CHECK (rating BETWEEN 1 AND 5),
    feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_trip_ratings_trip_id ON trip_ratings (trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_ratings_driver_id ON trip_ratings (driver_id);