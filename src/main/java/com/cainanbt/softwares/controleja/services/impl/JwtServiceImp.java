package com.cainanbt.softwares.controleja.services.impl;

import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.entities.Users;
import com.cainanbt.softwares.controleja.services.JwtService;
import com.cainanbt.softwares.controleja.utils.ConstsMessages;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtServiceImp implements JwtService {
    private final Key secret;
    private final long expiration;
    private final long refreshExpiration;
    private final String issueToken;

    public JwtServiceImp(
            @Value("${app.config.jwt.secret}") String secret,
            @Value("${app.config.jwt.expiration-ms}") long expiration,
            @Value("${app.config.jwt.refresh-expiration-ms}") long refreshExpiration,
            @Value("${app.config.token.issue}") String issueToken) {
        this.secret = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
        this.issueToken = issueToken;
    }

    @Override
    public String generateAccessToken(UserAuthenticateDTO userAuthenticate) {
        return buildToken(userAuthenticate, expiration);
    }
    @Override
    public String generateRefreshToken(UserAuthenticateDTO userAuthenticate) {
        return buildToken(userAuthenticate, refreshExpiration);
    }
    @Override
    public long getRefreshExpiration(){
        return refreshExpiration;
    }

    @Override
    public boolean validateToken(String token, UserAuthenticateDTO userAuthenticate) {
        final String username = extractUsername(token);
        return (username.equals(userAuthenticate.getUsername()) && !isTokenExpired(token));
    }

    @Override
    public String validateToken(String token) {
        return extractUsername(token);
    }

    @Override
    public boolean isValidTokenToLogin(String token) {
        try {
            Claims claims = extractAllClaims(token);

            if (claims.getIssuer() == null || !claims.getIssuer().equals(issueToken)) {
                return false;
            }

            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) {
                return false;
            }

            String username = claims.get("username", String.class);
            Object idObj = claims.get("id");
            String idStr = idObj != null ? idObj.toString() : null;

            if (username == null || username.isBlank() || idStr == null || idStr.isBlank()) {
                return false;
            }
            try {
                UUID.fromString(idStr);
            } catch (IllegalArgumentException ex) {
                return false;
            }

            return true;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public UserAuthenticateDTO getUserAuthenticateFromRefreshToken(String token) {
        Claims claims = extractAllClaims(token);

        String email = claims.get("username", String.class);
        String idStr = claims.get("id", String.class);

        if (email == null || email.isBlank() || idStr == null || idStr.isBlank()) {
            throw new IllegalArgumentException(ConstsMessages.MISSING_TOKEN_CLAIMS);
        }

        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(ConstsMessages.INVALID_UUID_TOKEN, ex);
        }

        Users user = Users.builder()
                .id(id)
                .email(email)
                .build();
        return new UserAuthenticateDTO(user);
    }

    private String buildToken(UserAuthenticateDTO userAuthenticate, long expirationTime) {
        return Jwts.builder()
                .setSubject(userAuthenticate.getUsername())
                .setIssuer(issueToken)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(secret, SignatureAlgorithm.HS256)
                .claim("id", userAuthenticate.getUsers().getId())
                .claim("username", userAuthenticate.getUsername())
                .compact();
    }

    private String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private boolean isTokenExpired(String token) {
        final Date expirationDate = extractClaim(token, Claims::getExpiration);
        return expirationDate.before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secret)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}