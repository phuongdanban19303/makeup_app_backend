package com.makeup.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.makeup.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private int status;
    private String code;
    private String message;
    private T data;
    private PageMeta meta;
    private String timestamp;
    private String path;
    @JsonProperty("trace_id")
    private String traceId;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .timestamp(Instant.now().toString())
                .path(resolveCurrentPath())
                .traceId(resolveTraceId())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .code(ErrorCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .timestamp(Instant.now().toString())
                .path(resolveCurrentPath())
                .traceId(resolveTraceId())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, PageMeta meta) {
        return ApiResponse.<T>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .code(ErrorCode.SUCCESS.getCode())
                .message(ErrorCode.SUCCESS.getMessage())
                .data(data)
                .meta(meta)
                .timestamp(Instant.now().toString())
                .path(resolveCurrentPath())
                .traceId(resolveTraceId())
                .build();
    }

    public static <T> ApiResponse<T> error(HttpStatus status, String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .status(status.value())
                .code(code)
                .message(message)
                .timestamp(Instant.now().toString())
                .path(resolveCurrentPath())
                .traceId(resolveTraceId())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode.getHttpStatus(), errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return error(errorCode.getHttpStatus(), errorCode.getCode(), customMessage);
    }

    private static String resolveCurrentPath() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest().getRequestURI();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String resolveTraceId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String traceHeader = attributes.getRequest().getHeader("X-Trace-Id");
                if (traceHeader != null && !traceHeader.isBlank()) {
                    return traceHeader;
                }
            }
        } catch (Exception ignored) {}
        return UUID.randomUUID().toString();
    }
}
