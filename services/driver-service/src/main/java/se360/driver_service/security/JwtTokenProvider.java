package se360.driver_service.security;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.public-key-path:/app/keys/public.pem}")
    private String publicKeyPath;

    private PublicKey publicKey;

    @PostConstruct
    public void loadKey() throws Exception {
        log.info("Loading public key from: {}", publicKeyPath);

        byte[] keyBytes;
        Path filePath = Paths.get(publicKeyPath);

        // Try filesystem first, then classpath
        if (Files.exists(filePath)) {
            log.info("Loading public key from filesystem: {}", publicKeyPath);
            keyBytes = Files.readAllBytes(filePath);
        } else {
            log.info("Loading public key from classpath: {}", publicKeyPath);
            ClassPathResource resource = new ClassPathResource(publicKeyPath);
            try (InputStream is = resource.getInputStream()) {
                keyBytes = is.readAllBytes();
            }
        }

        String keyString = new String(keyBytes)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\r?\\n", "")
                .trim();

        byte[] decodedKey = Base64.getDecoder().decode(keyString);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);

        log.info("Public key loaded successfully");
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public Jws<Claims> validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
    }
}
