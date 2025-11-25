package se360.driver_service.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import se360.driver_service.models.DriverLocationMessage;
import se360.driver_service.services.DriverService;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverLocationWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final DriverService driverService;

    public void afterConnectionEstablished(WebSocketSession session) {
        String driverId = (String) session.getAttributes().get("driverId");
        log.info("Driver WebSocket connected: {} (Driver: {})", session.getId(), driverId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            var payload = objectMapper.readValue(message.getPayload(), DriverLocationMessage.class);

            String authenticatedDriverId = (String) session.getAttributes().get("driverId");

            if (!payload.driverId().equals(authenticatedDriverId)) {
                log.warn("Driver {} attempted to send location for driver {}", authenticatedDriverId,
                        payload.driverId());
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid driver identity"));
                return;
            }

            driverService.handleStreamingLocation(payload);
        } catch (Exception ex) {
            log.warn("Failed to process WS message: {}", message.getPayload());
            log.error("Exception details:", ex);
            session.close(CloseStatus.BAD_DATA);
        }
    }

    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket transport error: {}", session.getId(), exception);
    }
}
