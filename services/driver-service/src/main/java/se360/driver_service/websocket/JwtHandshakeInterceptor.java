package se360.driver_service.websocket;

import java.net.URI;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import org.springframework.web.socket.WebSocketHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import se360.driver_service.security.JwtTokenProvider;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider tokenProvider;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        log.info("Starting WebSocket handshake");

        URI uri = request.getURI();
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        String token = params.getFirst("token");
        if (token == null)
            return false;

        try {
            Jws<Claims> jws = tokenProvider.validateToken(token);
            Claims claims = jws.getPayload();
            log.info("JWT validated successfully for driverId: {}", claims.getSubject());

            attributes.put("driverId", claims.getSubject());
            attributes.put("role", claims.get("role"));

            if (!"DRIVER".equals(claims.get("role"))) {
                return false;
            }
            log.info("WebSocket handshake successful for driverId: {}", claims.getSubject());

            return true;
        } catch (Exception ex) {
            log.warn("JWT validation failed during WebSocket handshake: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }

}
