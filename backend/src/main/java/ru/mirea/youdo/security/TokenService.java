package ru.mirea.youdo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import ru.mirea.youdo.config.YoudoProperties;

@Service
public class TokenService {
    private final SecretKey key;
    private final YoudoProperties properties;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public TokenService(YoudoProperties properties, Clock clock) {
        this.properties=properties; this.clock=clock;
        try { this.key=Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.jwtSecret())); }
        catch (RuntimeException e) { throw new IllegalStateException("JWT_SECRET must be base64 of at least 32 random bytes"); }
    }
    public String access(AccountCredentials account) {
        var now=clock.instant();
        return Jwts.builder().issuer(properties.issuer()).subject(account.id().toString())
            .id(UUID.randomUUID().toString()).issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(properties.accessDuration())))
            .claim("cv",account.credentialsVersion()).signWith(key, Jwts.SIG.HS256).compact();
    }
    public Claims parse(String jwt) {
        return Jwts.parser().verifyWith(key).requireIssuer(properties.issuer())
            .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(jwt).getPayload();
    }
    public String refresh() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
