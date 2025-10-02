package com.capstone.api_gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.capstone.api_gateway.service.TokenBlacklistService;

import reactor.core.publisher.Mono;
import javax.crypto.SecretKey;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtFilter implements GlobalFilter, Ordered {

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    private final TokenBlacklistService tokenBlacklistService;

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.debug("JwtFilter processing request for path: {}", path);

        // Skip JWT validation only for infrastructure endpoints
        if (isInfrastructureEndpoint(path)) {
            log.debug("Skipping JWT validation for infrastructure endpoint: {}", path);
            return chain.filter(getSanitizedAuthExchange(exchange));
        }

        // Extract JWT token (cookie first, then header fallback)
        String jwt = getJwtFromRequest(exchange.getRequest());

        if (!StringUtils.hasText(jwt)) {
            log.debug("No JWT token found for path: {} - delegating to service", path);
            return chain.filter(getSanitizedAuthExchange(exchange));
        }

        // JWT token present - validate it
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();

            // Verify this is an ACCESS token
            String tokenType = claims.get("type", String.class);
            if (!"ACCESS".equals(tokenType)) {
                log.warn("Invalid token type: {} for path: {}", tokenType, path);
                return chain.filter(getSanitizedAuthExchange(exchange));
            }

            // Extract JTI and check blacklist
            String jti = claims.getId();
            return tokenBlacklistService.isTokenBlacklisted(jti)
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            log.warn("Rejected blacklisted token with JTI: {}", jti);
                            return chain.filter(getSanitizedAuthExchange(exchange));
                        }

                        // Extract user information
                        String userId = claims.getSubject();
                        String role = claims.get("role", String.class);
                        String email = claims.get("email", String.class);

                        log.debug("JWT validated successfully - UserId: {}, Role: {}, Email: {}", userId, role, email);

                        // Add user context headers for downstream services
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Role", role)
                                .header("X-User-Email", email)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired for path: {}", path);
            return chain.filter(getSanitizedAuthExchange(exchange));
        } catch (JwtException e) {
            log.error("JWT validation failed for path: {}: {}", path, e.getMessage());

            return chain.filter(getSanitizedAuthExchange(exchange));
        }
    }

    /**
     * Extract JWT token from cookies first, then fallback to Authorization header
     */
    private String getJwtFromRequest(ServerHttpRequest request) {
        // Try to get token from cookie
        MultiValueMap<String, HttpCookie> cookies = request.getCookies();
        if (cookies.containsKey(ACCESS_TOKEN_COOKIE_NAME)) {
            HttpCookie accessTokenCookie = cookies.getFirst(ACCESS_TOKEN_COOKIE_NAME);
            if (accessTokenCookie != null && StringUtils.hasText(accessTokenCookie.getValue())) {
                log.debug("Access token found in cookie");
                return accessTokenCookie.getValue();
            }
        }

        // Fallback to Authorization header (for API clients)
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            log.debug("Access token found in Authorization header");
            return authHeader.substring(7);
        }

        log.debug("No access token found in cookie or Authorization header");
        return null;
    }

    /**
     * Only infrastructure-level endpoints that should skip JWT validation at gateway level
     */
    private boolean isInfrastructureEndpoint(String path) {
        return path.startsWith("/actuator/health") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/webjars/") ||
                path.equals("/favicon.ico");
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    private ServerWebExchange getSanitizedAuthExchange(ServerWebExchange exchange) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                    h.remove("X-User-Email");
                })
                .build();
        log.info("Sanitized request headers: {}", mutatedRequest.getHeaders());
        return exchange.mutate().request(mutatedRequest).build();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}