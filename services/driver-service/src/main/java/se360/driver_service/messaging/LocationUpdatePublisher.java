package se360.driver_service.messaging;

import se360.driver_service.models.DriverLocationMessage;

public interface LocationUpdatePublisher {
    void publishLocationUpdate(DriverLocationMessage message);
}
