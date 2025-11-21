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
        log.info("Driver WebSocket connected: {}", session.getId());
        // TODO: authenticate + attach driverId to session attributes if needed

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            var payload = objectMapper.readValue(message.getPayload(), DriverLocationMessage.class);
            // TODO: cross-check payload.driverId with authenticated driver in session
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
