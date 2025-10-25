package se360.trip_service.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class EstimateFareResponse {

    private BigDecimal distanceKm;
    private BigDecimal estimatedPrice;
}
