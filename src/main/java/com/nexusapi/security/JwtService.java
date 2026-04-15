ackage com.nexusapi.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles JWT creation, parsing, and validation.
 *
 * <p>Uses HMAC-SHA256 (HS256) signing with a configurable secret key.
 * The secret must be at least 256 bits (32 ASCII characters) to satisfy
 * the HS256 minimum key length requirement.
 *
 * <p>Token structure:
 * <pre>
 *   Header:  { "alg": "HS256", "typ": "JWT" }
 *   Payload: { "sub": "user@email.com", "role": "USER",
 *              "iat": ..., "exp": ... }
 *   Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
 * </pre>
 */
@Service
@Slf4j
public class JwtService {

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.expiration}")
    private long jwtExpiration;

    // ---------------------------------------------------------------------------
    // Token generation
    // ---------------------------------------------------------------------------

    /**
     * Generates a signed JWT for the given user.
     *
     * @param userDetails the authenticated user
     * @return a compact, URL-safe JWT string
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        // Embed the role in the token so downstream services can authorise
        // without a database lookup on every request.
        extraClaims.put("role", userDetails.getAuthorities().stream()
            .findFirst()
            .map(Object::toString)
            .orElse("ROLE_USER"));
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Constructs the JWT with the given claims and expiry.
     *
     * @param extraClaims additional claims to embed in the payload
     * @param userDetails the subject of the token
     * @param expiration  token lifetime in milliseconds
     * @return signed compact JWT
     */
    private String buildToken(
        Map<String, Object> extraClaims,
        UserDetails userDetails,
        long expiration
    ) {
        return Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.getUsername())
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    // ---------------------------------------------------------------------------
    // Token validation
    // ---------------------------------------------------------------------------

    /**
     * Validates a token against the provided user details.
     *
     * <p>Checks:
     * <ol>
     *   <li>The subject matches the user's username (email)</li>
     *   <li>The token has not expired</li>
     *   <li>The signature is valid (implicit in {@link #extractAllClaims})</li>
     * </ol>
     *
     * @param token       the JWT to validate
     * @param userDetails the user to validate against
     * @return {@code true} if the token is valid for this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------------
    // Claims extraction
    // ---------------------------------------------------------------------------

    /**
     * Extracts the subject (email) from the token.
     *
     * @param token the JWT string
     * @return the subject claim value
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generic claim extractor using a resolver function.
     *
     * @param token          the JWT string
     * @param claimsResolver function to extract a specific claim
     * @param <T>            the type of the extracted claim
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses and verifies the JWT, returning all claims.
     *
     * @param token the JWT string
     * @return all claims from the verified token
     * @throws JwtException if the token is malformed, expired, or has an invalid signature
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Derives the HMAC signing key from the base64-encoded secret.
     * The key is derived once and reused — {@link Keys#hmacShaKeyFor}
     * validates the key length against the algorithm requirements.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
