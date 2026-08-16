package com.makeup.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeup.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Endpoints công khai không yêu cầu token xác thực
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/v1/auth/**",
            "/api/v1/workers/nearby",
            "/api/v1/mua/*/profile",
            "/api/v1/mua/*/services",
            "/api/v1/mua/*/portfolio",
            "/api/v1/pricing/estimate",
            "/ws-location/**",
            "/ws/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/fallback/**"
    );


    // Endpoints dành riêng cho Thợ Makeup (MUA)
    private static final List<String> MUA_ONLY_ENDPOINTS = List.of(
            "/api/v1/mua/*/profile",
            "/api/v1/mua/*/upload-identity-card",
            "/api/v1/mua/*/services",
            "/api/v1/mua/*/services/**",
            "/api/v1/mua/*/portfolio",
            "/api/v1/mua/*/portfolio/**",
            "/api/v1/bookings/*/accept",
            "/api/v1/bookings/*/reject",
            "/api/v1/bookings/*/start-moving",
            "/api/v1/bookings/*/arrived",
            "/api/v1/bookings/*/start-makeup",
            "/api/v1/bookings/*/complete",
            "/api/v1/workers/location/stream",
            "/api/v1/location/stream",
            "/api/v1/wallets/*/deduct-commission"
    );

    // Endpoints dành riêng cho Khách hàng (Customer)
    private static final List<String> CUSTOMER_ONLY_ENDPOINTS = List.of(
            "/api/v1/bookings/request",
            "/api/v1/bookings"
    );

    // Endpoints dành riêng cho Quản trị viên (Admin)
    private static final List<String> ADMIN_ONLY_ENDPOINTS = List.of(
            "/api/v1/users",
            "/api/v1/bookings/*/status"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // Lấy hoặc tự tạo Trace ID để định vết request xuyên suốt các microservices
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        // 1. Bỏ qua xác thực cho HTTP OPTIONS (CORS Preflight) và các endpoint công khai
        if ("OPTIONS".equalsIgnoreCase(method) || isPublicEndpoint(path, method)) {
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-Trace-Id", traceId)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // 2. Kiểm tra header Authorization
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Unauthorized request to {}: Missing or invalid Authorization header", path);
            return handleUnauthenticated(exchange, "Yêu cầu cung cấp Token xác thực hợp lệ (Authorization: Bearer <token>)");
        }

        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            log.warn("Unauthorized request to {}: Invalid or expired token", path);
            return handleUnauthenticated(exchange, "Token xác thực không hợp lệ hoặc đã hết hạn");
        }

        // 3. Trích xuất thông tin User ID và Roles từ JWT
        String userId = jwtUtils.getUserIdFromToken(token);
        List<String> roles = jwtUtils.getRolesFromToken(token);

        log.info("Request path: {} [{}] | Trace ID: {} | User ID: {} | Roles: {}", path, method, traceId, userId, roles);

        // 4. Kiểm tra phân quyền dựa theo Role (Authorization)
        if (!isAuthorized(path, method, roles)) {
            log.warn("Forbidden request to {} [{}]: User {} with roles {} is not authorized", path, method, userId, roles);
            return handleForbidden(exchange, "Tài khoản của bạn không có quyền truy cập chức năng này");
        }

        // 5. Chuyển tiếp thông tin user & traceId xuống các microservices phía sau qua Headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", userId != null ? userId : "")
                .header("X-User-Roles", String.join(",", roles))
                .header("X-Trace-Id", traceId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicEndpoint(String path, String method) {
        if ((path.startsWith("/api/v1/mua/") || path.startsWith("/api/v1/workers/")) && !"GET".equalsIgnoreCase(method)) {
            return false;
        }
        return PUBLIC_ENDPOINTS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isAuthorized(String path, String method, List<String> roles) {
        boolean isAdmin = roles.stream().anyMatch(r -> r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("ROLE_ADMIN"));
        boolean isMua = roles.stream().anyMatch(r -> r.equalsIgnoreCase("MUA") || r.equalsIgnoreCase("ROLE_MUA"));
        boolean isCustomer = roles.stream().anyMatch(r -> r.equalsIgnoreCase("CUSTOMER") || r.equalsIgnoreCase("ROLE_CUSTOMER"));

        // Admin có full quyền truy cập tất cả tính năng
        if (isAdmin) {
            return true;
        }

        // Endpoint dành riêng cho Admin
        if (isAdminOnlyEndpoint(path, method)) {
            return false;
        }

        // Endpoint dành riêng cho Thợ Makeup
        if (isMuaOnlyEndpoint(path, method)) {
            return isMua;
        }

        // Endpoint dành riêng cho Khách hàng
        if (isCustomerOnlyEndpoint(path, method)) {
            return isCustomer;
        }

        // Các API dùng chung khác (xem profile MUA, tính giá, vị trí thợ lân cận, xem số dư ví, v.v.)
        return isCustomer || isMua;
    }

    private boolean isAdminOnlyEndpoint(String path, String method) {
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/users")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/api/v1/users")) {
            return true;
        }
        return ADMIN_ONLY_ENDPOINTS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isMuaOnlyEndpoint(String path, String method) {
        if (path.startsWith("/api/v1/mua/") && ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return true;
        }
        return MUA_ONLY_ENDPOINTS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isCustomerOnlyEndpoint(String path, String method) {
        if ("POST".equalsIgnoreCase(method) && (path.equals("/api/v1/bookings/request") || path.equals("/api/v1/bookings"))) {
            return true;
        }
        return CUSTOMER_ONLY_ENDPOINTS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> handleUnauthenticated(ServerWebExchange exchange, String message) {
        return buildErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    private Mono<Void> handleForbidden(ServerWebExchange exchange, String message) {
        return buildErrorResponse(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private Mono<Void> buildErrorResponse(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> apiResponse = ApiResponse.error(status, code, message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"status\":" + status.value() + ",\"code\":\"" + code + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
