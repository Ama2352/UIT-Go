package se360.driver_service.security;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;

@Component
public class JwtTokenProvider {

    private PublicKey publicKey;

    @PostConstruct
    public void loadKey() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("keys/public.pem");
        byte[] keyBytes = is.readAllBytes();

        String keyString = new String(keyBytes)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decodedKey = Base64.getDecoder().decode(keyString);

        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public Jws<Claims> validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
    }
}
