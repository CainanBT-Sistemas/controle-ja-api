package com.cainanbt.softwares.controleja.services.impl;


import com.cainanbt.softwares.controleja.dtos.UserAuthenticateDTO;
import com.cainanbt.softwares.controleja.services.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
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
        final String username = extractUsername(token);
        return username;
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

    private String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("id", String.class);
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
