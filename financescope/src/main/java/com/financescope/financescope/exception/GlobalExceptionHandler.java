package com.financescope.financescope.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.error("비즈니스 예외 발생: {}", e.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .errorCode(e.getErrorCode() != null ? e.getErrorCode() : "BUSINESS_ERROR")
                .message(e.getMessage())
                .timestamp(LocalDateTime.now())
                .data(e.getData())
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.error("유효성 검사 실패: {}", e.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .errorCode("VALIDATION_ERROR")
                .message("입력값이 올바르지 않습니다.")
                .timestamp(LocalDateTime.now())
                .data(errors)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException e) {
        log.error("인증 실패: {}", e.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .errorCode("AUTH_ERROR")
                .message("인증에 실패했습니다.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.error("접근 권한 없음: {}", e.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .errorCode("ACCESS_DENIED")
                .message("접근 권한이 없습니다.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        log.error("예상치 못한 오류 발생: ", e);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .success(false)
                .errorCode("INTERNAL_ERROR")
                .message("서버 내부 오류가 발생했습니다.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // ErrorResponse 클래스
    public static class ErrorResponse {
        private Boolean success;
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
        private Object data;

        public ErrorResponse() {}

        private ErrorResponse(Builder builder) {
            this.success = builder.success;
            this.errorCode = builder.errorCode;
            this.message = builder.message;
            this.timestamp = builder.timestamp;
            this.data = builder.data;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public Boolean getSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Object getData() { return data; }

        // Builder 클래스
        public static class Builder {
            private Boolean success;
            private String errorCode;
            private String message;
            private LocalDateTime timestamp;
            private Object data;

            public Builder success(Boolean success) {
                this.success = success;
                return this;
            }

            public Builder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public Builder data(Object data) {
                this.data = data;
                return this;
            }

            public ErrorResponse build() {
                return new ErrorResponse(this);
            }
        }
    }
}