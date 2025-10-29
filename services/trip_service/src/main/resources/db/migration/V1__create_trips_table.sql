CREATE TABLE IF NOT EXISTS trips (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- References (no FK - cross-database)
    passenger_id UUID NOT NULL,
    driver_id UUID,

    -- Pickup location
    pickup_lat DECIMAL(10, 8) NOT NULL,
    pickup_lng DECIMAL(10, 8) NOT NULL,
    pickup_address TEXT NOT NULL,

    -- Dropoff location
    dropoff_lat DECIMAL(10, 8) NOT NULL,
    dropoff_lng DECIMAL(10, 8) NOT NULL,
    dropoff_address TEXT NOT NULL,

    -- Trip details
    vehicle_type VARCHAR(50) NOT NULL,
    trip_status VARCHAR(20) NOT NULL DEFAULT 'searching',

    -- Pricing
    distance_km DECIMAL(6, 2),
    estimated_price DECIMAL(10, 2),
    final_price DECIMAL(10, 2),

    -- Cancellation
    cancelled_by VARCHAR(20),
    cancelled_at TIMESTAMP,

    -- Audit timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Soft delete
    is_deleted BOOLEAN DEFAULT FALSE,

    -- Optional fields for scaling
    request_id UUID,
    version INT DEFAULT 1
    );

-- Indexes for optimization
CREATE INDEX IF NOT EXISTS idx_trips_passenger_id ON trips (passenger_id);
CREATE INDEX IF NOT EXISTS idx_trips_driver_id ON trips (driver_id) WHERE driver_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips (trip_status);
CREATE INDEX IF NOT EXISTS idx_trips_created_at ON trips (created_at);
CREATE INDEX IF NOT EXISTS idx_trips_searching ON trips (trip_status, created_at) WHERE trip_status = 'searching';
