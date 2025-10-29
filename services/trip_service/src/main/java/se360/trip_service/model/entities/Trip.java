package se360.trip_service.model.entities;

import jakarta.persistence.*;
import lombok.*;
import se360.trip_service.model.enums.TripStatus;
import se360.trip_service.model.enums.VehicleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trips",
        indexes = {
                @Index(name = "idx_trips_passenger_id", columnList = "passenger_id"),
                @Index(name = "idx_trips_driver_id", columnList = "driver_id"),
                @Index(name = "idx_trips_status", columnList = "trip_status"),
                @Index(name = "idx_trips_created_at", columnList = "created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    // ===== PRIMARY KEY =====
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID DEFAULT gen_random_uuid()")
    private UUID id;

    // ===== REFERENCES =====
    @Column(name = "passenger_id", nullable = false)
    private UUID passengerId;

    @Column(name = "driver_id")
    private UUID driverId;

    // ===== PICKUP LOCATION =====
    @Column(name = "pickup_lat", precision = 10, scale = 8, nullable = false)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", precision = 10, scale = 8, nullable = false)
    private BigDecimal pickupLng;

    @Column(name = "pickup_address", columnDefinition = "TEXT", nullable = false)
    private String pickupAddress;

    // ===== DROPOFF LOCATION =====
    @Column(name = "dropoff_lat", precision = 10, scale = 8, nullable = false)
    private BigDecimal dropoffLat;

    @Column(name = "dropoff_lng", precision = 10, scale = 8, nullable = false)
    private BigDecimal dropoffLng;

    @Column(name = "dropoff_address", columnDefinition = "TEXT", nullable = false)
    private String dropoffAddress;

    // ===== TRIP DETAILS =====
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 50, nullable = false)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_status", length = 20, nullable = false)
    private TripStatus tripStatus = TripStatus.SEARCHING;

    // ===== PRICING =====
    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "estimated_price", precision = 10, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "final_price", precision = 10, scale = 2)
    private BigDecimal finalPrice;

    // ===== CANCELLATION =====
    @Column(name = "cancelled_by", length = 20)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // ===== TIMESTAMPS =====
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ===== SOFT DELETE =====
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    // ===== OPTIONAL FIELDS =====
    @Column(name = "request_id")
    private UUID requestId;

    // ===== OPTIMISTIC LOCKING =====
    @Version
    private int version;
}
