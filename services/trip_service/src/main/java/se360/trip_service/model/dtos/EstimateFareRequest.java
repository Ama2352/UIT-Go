package se360.trip_service.model.dtos;

import lombok.Data;
import se360.trip_service.model.enums.VehicleType;
import java.math.BigDecimal;

@Data
public class EstimateFareRequest {
    private String pickupAddress;
    private String dropoffAddress;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private VehicleType vehicleType;
}
