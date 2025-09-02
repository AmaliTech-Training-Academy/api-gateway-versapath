package com.capstone.api_gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.core.io.buffer.DataBuffer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.capstone.api_gateway.dto.ApiResponseDto;
import com.capstone.api_gateway.service.TokenBlacklistService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtFilter implements GlobalFilter, Ordered {

    @Value("${JWT_SECRET}")
    private String jwtSecret;
    
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        log.debug("JwtFilter processing request for path: {}", path);

        // Skip JWT validation only for infrastructure endpoints
        if (isInfrastructureEndpoint(path)) {
            log.debug("Skipping JWT validation for infrastructure endpoint: {}", path);
            return chain.filter(exchange);
        }

        // Get Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No JWT token found for path: {} - delegating to service", path);
            return chain.filter(exchange);
        }

        // JWT token present - validate it
        String jwt = authHeader.substring(7);

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
                return unauthorizedResponse(exchange);
            }

            // Extract JTI and check blacklist
            String jti = claims.getId();
            return tokenBlacklistService.isTokenBlacklisted(jti)
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            log.warn("Rejected blacklisted token with JTI: {}", jti);
                            return unauthorizedResponse(exchange);
                        }

                        // Extract user information
                        String userId = claims.getSubject();
                        String role = claims.get("role", String.class);
                        String email = claims.get("email", String.class);

                        log.debug("JWT validated successfully - UserId: {}, Role: {}, Email: {}", userId, role, email);

                        // Add user context headers
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Role", role)
                                .header("X-User-Email", email)
                                .build();

                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    });

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired for path: {}", path);
            return unauthorizedResponse(exchange);
        } catch (JwtException e) {
            log.error("JWT validation failed for path: {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange);
        }
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

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");

        ApiResponseDto<Void> errorResponse = ApiResponseDto.error(
            "Invalid or missing authentication token",
            "Authentication required"
        );

        try {
            String body = objectMapper.writeValueAsString(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing unauthorized response: {}", e.getMessage());
            // Fallback to simple response
            String fallbackBody = "{\"success\":false,\"message\":\"Authentication required\"}";
            DataBuffer buffer = response.bufferFactory().wrap(fallbackBody.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

